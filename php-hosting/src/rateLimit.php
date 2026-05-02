<?php

/**
 * APCu-backed token-bucket rate limiter.
 *
 * Keyed by (apiKey, route), enforces requests-per-second with burst allowance.
 * Falls back to no-op if APCu is unavailable — a startup guard in index.php
 * refuses to boot when RATE_LIMIT_ENABLED=true but APCu is missing.
 *
 * Bucket structure stored in APCu:
 *   { tokens: float, lastRefill: float (microtime) }
 */

/**
 * Enforce a rate limit. Responds 429 + exits if the bucket is exhausted.
 *
 * @param string $key  Unique identifier (e.g. "track:{$projectId}")
 * @param int    $rps  Requests per second (sustained rate)
 * @param int    $burst Maximum burst tokens
 */
function enforceLimit(string $key, int $rps, int $burst): void
{
    if (!defined('RATE_LIMIT_ENABLED') || !RATE_LIMIT_ENABLED) {
        return;
    }
    if (!function_exists('apcu_fetch')) {
        // Guarded at startup — this is a safety belt
        error_log('[QuietMetrix] Rate limit enabled but APCu unavailable — request allowed');
        return;
    }

    $bucket = apcu_fetch("qm_limit:$key");

    $now = microtime(true);

    if ($bucket === false) {
        $bucket = [
            'tokens'     => (float)$burst,
            'lastRefill' => $now,
        ];
    }

    // Refill tokens
    $elapsed = $now - $bucket['lastRefill'];
    $bucket['tokens'] = min((float)$burst, $bucket['tokens'] + $elapsed * (float)$rps);
    $bucket['lastRefill'] = $now;

    if ($bucket['tokens'] >= 1.0) {
        $bucket['tokens'] -= 1.0;
        apcu_store("qm_limit:$key", $bucket, max(60, (int)ceil($burst / max(1, $rps)) + 30));
        return;
    }

    // Bucket exhausted
    $retryAfter = max(1, (int)ceil((1.0 - $bucket['tokens']) / max(1, $rps)));
    apcu_store("qm_limit:$key", $bucket, max(60, $retryAfter + 30));

    header('Retry-After: ' . $retryAfter);
    errorResponse(429, 'rate_limit_exceeded', 'Rate limit exceeded. Retry after ' . $retryAfter . ' seconds.');
    exit;
}

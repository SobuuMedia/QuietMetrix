<?php

/**
 * Database-coordinated bounded fixed-window ingest limiter.
 *
 * Keyed by (apiKey, route), shares a fixed-minute request budget across workers.
 * Database failures propagate; the limiter never silently allows traffic.
 */

/**
 * Enforce a rate limit. Responds 429 + exits if the bucket is exhausted.
 *
 * @param string $key  Unique identifier (e.g. "track:{$projectId}")
 * @param int    $rps  Per-second configuration converted to a minute budget
 * @param int    $burst Maximum requests in the minute window
 */
function enforceLimit(string $key, int $rps, int $burst): void
{
    if (!defined('RATE_LIMIT_ENABLED') || !RATE_LIMIT_ENABLED) {
        return;
    }
    enforceSharedLimit('ingest:' . $key, min($burst, $rps * 60));
}

/** Shared across PHP workers/replicas; no raw IP or email is retained. */
function enforceSharedLimit(string $key, int $limit, int $window = 60): void {
    $db = getDb(); $now = time();
    $bucket = hash_hmac('sha256', $key . ':' . intdiv($now, $window), JWT_SECRET);
    $db->beginTransaction();
    try {
        $db->prepare('DELETE FROM request_rate_buckets WHERE expires_at<=?')->execute([$now]);
        if ((int)$db->query('SELECT COUNT(*) FROM request_rate_buckets')->fetchColumn() >= 100000) {
            $existing=$db->prepare('SELECT 1 FROM request_rate_buckets WHERE bucket_key=?'); $existing->execute([$bucket]);
            if ($existing->fetchColumn() === false) { $db->rollBack(); header('Retry-After: 60'); errorResponse(429,'rate_limit_exceeded','Rate limiter capacity reached'); exit; }
        }
        $db->prepare('INSERT IGNORE INTO request_rate_buckets (bucket_key,attempts,expires_at) VALUES (?,0,?)')->execute([$bucket,(intdiv($now,$window)+2)*$window]);
        $db->prepare('UPDATE request_rate_buckets SET attempts=attempts+1 WHERE bucket_key=?')->execute([$bucket]);
        $stmt=$db->prepare('SELECT attempts FROM request_rate_buckets WHERE bucket_key=?'); $stmt->execute([$bucket]);
        $allowed=(int)$stmt->fetchColumn() <= $limit;
        $db->commit();
    } catch (Throwable $e) { $db->rollBack(); throw $e; }
    if (!$allowed) { header('Retry-After: ' . $window); errorResponse(429,'rate_limit_exceeded','Request budget exhausted'); exit; }
}

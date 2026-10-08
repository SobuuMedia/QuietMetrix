<?php

/** Returns a v4 UUID (RFC 4122). */
function uuid4(): string {
    $bytes = random_bytes(16);
    $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40); // version 4
    $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80); // variant 10xx
    $hex = bin2hex($bytes);
    return sprintf(
        '%s-%s-%s-%s-%s',
        substr($hex,  0, 8),
        substr($hex,  8, 4),
        substr($hex, 12, 4),
        substr($hex, 16, 4),
        substr($hex, 20, 12)
    );
}

/** Current UTC timestamp matching Kotlin's Instant.now().toString(). */
function now(): string {
    return gmdate('Y-m-d\TH:i:s\Z');
}

/**
 * Name of the config file index.php/diag.php should require, relative to the
 * php-hosting/ root. Defaults to 'config.php'; set the QM_CONFIG_FILE env var to
 * point local/e2e testing at a distinctly-named file instead (e.g. config.e2e.php)
 * so a file literally named config.php never needs to exist in a working copy used
 * for manual testing -- see tests/configFilePathTest.php for why this matters.
 * An empty value or one containing a path separator (accidental escape outside
 * php-hosting/) is ignored and falls back to the safe default.
 */
function configFilePath(): string {
    $override = getenv('QM_CONFIG_FILE');
    if ($override === false || $override === '') {
        return 'config.php';
    }
    if (strpos($override, '/') !== false || strpos($override, '\\') !== false) {
        return 'config.php';
    }
    return $override;
}

/** Generates a 64-character random hex token, suitable for API keys. */
function randomToken(): string {
    return bin2hex(random_bytes(32));
}

/**
 * SHA-256 hex of an opaque high-entropy token. Used for API keys: the
 * tokens are 256-bit random so brute-force is infeasible, and a fast equality
 * lookup (vs bcrypt's per-row work-factor compare) lets the ingest hot path
 * authenticate in a single indexed query.
 */
function tokenHash(string $token): string {
    return hash('sha256', $token);
}

function jsonResponse(int $status, $data): void {
    http_response_code($status);
    header('Content-Type: application/json');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
}

function errorResponse(int $status, string $code, string $message = ''): void {
    jsonResponse($status, [
        'error'   => $code,
        'message' => $message,
    ]);
}

/**
 * Reads and decodes the request body once, caches for the request lifetime.
 * Refuses bodies over 1 MB before reading them into memory.
 */
function getJsonBody(): array {
    static $body = null;
    if ($body === null) {
        $contentLength = (int)($_SERVER['CONTENT_LENGTH'] ?? 0);
        if ($contentLength > 1048576) {
            errorResponse(413, 'payload_too_large', 'Request body exceeds 1 MB');
            exit;
        }
        // Bound the actual read as well as trusting Content-Length: chunked requests and
        // clients without that header must not bypass the 1 MiB limit.
        $stream = fopen('php://input', 'rb');
        $raw = '';
        if (is_resource($stream)) {
            while (!feof($stream) && strlen($raw) <= 1048576) {
                $chunk = fread($stream, min(8192, 1048577 - strlen($raw)));
                if ($chunk === false || $chunk === '') break;
                $raw .= $chunk;
            }
        }
        if (is_resource($stream)) fclose($stream);
        if (is_string($raw) && strlen($raw) > 1048576) {
            errorResponse(413, 'payload_too_large', 'Request body exceeds 1 MB');
            exit;
        }
        $body = json_decode($raw ?: '{}', true) ?? [];
    }
    return $body;
}

/** Best-effort client IP. Trusts X-Forwarded-For only when REMOTE_ADDR matches
 *  a configured trusted proxy; otherwise returns REMOTE_ADDR directly. */
function clientIp(): ?string {
    $trusted = defined('TRUSTED_PROXIES') ? @unserialize(TRUSTED_PROXIES) : [];
    if (!is_array($trusted)) $trusted = [];

    $remoteAddr = $_SERVER['REMOTE_ADDR'] ?? null;
    if (in_array($remoteAddr, $trusted, true)) {
        $xff = $_SERVER['HTTP_X_FORWARDED_FOR'] ?? '';
        if ($xff !== '') {
            $hops = array_map('trim', explode(',', $xff));
            if (count($hops) > 20) return $remoteAddr;
            foreach ($hops as $hop) if (!filter_var($hop, FILTER_VALIDATE_IP)) return $remoteAddr;
            foreach (array_reverse($hops) as $hop) if (!in_array($hop, $trusted, true)) return $hop;
        }
    }
    return $remoteAddr;
}

/**
 * Normalizes the ALLOWED_ORIGINS constant to a comma-separated string, whatever
 * shape it was defined in. Older shared-hosting configs used serialize([...]);
 * current configs use a plain comma-separated string. Returns '' if unset or
 * unusable, which callers treat as "no allowlist configured".
 */
function allowedOriginsCsv(): string {
    if (!defined('ALLOWED_ORIGINS')) return '';
    $configured = constant('ALLOWED_ORIGINS');
    if (is_array($configured)) {
        return implode(',', $configured);
    }
    if (!is_string($configured)) return '';
    $legacy = @unserialize($configured);
    return is_array($legacy) ? implode(',', $legacy) : $configured;
}

/**
 * Picks the Access-Control-Allow-Origin value for a response, given the
 * request's Origin header, the legacy single pinned origin (ALLOWED_ORIGIN),
 * and the Stage 5 allowlist (ALLOWED_ORIGINS, comma-separated). '*' on either
 * config wins outright. With an allowlist configured, a matching request
 * origin is echoed back (required for the browser to accept a non-wildcard
 * CORS response); a non-matching or missing origin falls back to the pinned
 * origin. With no allowlist, the pinned origin is always returned unchanged
 * (legacy single-origin behaviour). Returns null when there is nothing safe
 * to send, meaning callers should omit the header entirely.
 */
function resolveCorsOrigin(?string $requestOrigin, string $pinnedOrigin, string $allowlist): ?string {
    $pinned = trim($pinnedOrigin);
    if ($pinned === '*') return '*';

    $allowed = array_values(array_filter(array_map(
        static fn($value) => trim($value),
        explode(',', $allowlist),
    ), static fn($value) => $value !== ''));

    if (in_array('*', $allowed, true)) return '*';

    if (!empty($allowed) && $requestOrigin !== null && in_array($requestOrigin, $allowed, true)) {
        return $requestOrigin;
    }

    return $pinned !== '' ? $pinned : null;
}

/**
 * Stage 5 — true if a browser-originated request's Origin is allowed to post
 * ingest events. No Origin header (mobile/native SDKs) is always allowed. An
 * empty allowlist means the check isn't enforced; '*' allows any origin.
 */
function isIngestOriginAllowed(?string $requestOrigin, string $allowlist): bool {
    if ($requestOrigin === null) return true;

    $allowed = array_values(array_filter(array_map(
        static fn($value) => trim($value),
        explode(',', $allowlist),
    ), static fn($value) => $value !== ''));

    if (empty($allowed)) return true;
    return in_array('*', $allowed, true) || in_array($requestOrigin, $allowed, true);
}

/** Stage 5 — origin/referer check for non-mobile traffic, shared by every ingest route. */
function checkOrigin(): void {
    $origin = $_SERVER['HTTP_ORIGIN'] ?? ($_SERVER['HTTP_REFERER'] ?? null);
    if (!isIngestOriginAllowed($origin, allowedOriginsCsv())) {
        errorResponse(403, 'origin_forbidden', 'Origin not allowed');
        exit;
    }
}

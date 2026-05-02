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
        $raw  = file_get_contents('php://input');
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
            $first = trim(explode(',', $xff)[0]);
            if ($first !== '') return $first;
        }
    }
    return $remoteAddr;
}

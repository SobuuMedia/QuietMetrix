<?php

// Pure-PHP HMAC-SHA256 JWT — no Composer, no extensions required.
// Kept compatible with the issuer/audience used by the Ktor server so the
// dashboard can target either backend with the same token shape.

const JWT_ISSUER   = 'quietmetrix';
const JWT_AUDIENCE = 'quietmetrix-dashboard';

function _b64url_encode(string $data): string {
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

function _b64url_decode(string $data): string {
    $padded = str_pad($data, strlen($data) + (4 - strlen($data) % 4) % 4, '=');
    return base64_decode(strtr($padded, '-_', '+/'));
}

/**
 * Encode a JWT for the given subject (user id) and role.
 * `iss`, `aud`, `iat`, `exp` are populated automatically.
 */
function jwt_encode(string $subject, string $email, string $role, string $secret): string {
    $header  = _b64url_encode(json_encode(['typ' => 'JWT', 'alg' => 'HS256']));
    $payload = _b64url_encode(json_encode([
        'iss'   => JWT_ISSUER,
        'aud'   => JWT_AUDIENCE,
        'sub'   => $subject,
        'email' => $email,
        'role'  => $role,
        'iat'   => time(),
        'exp'   => time() + JWT_EXPIRY_HOURS * 3600,
    ]));
    $signing   = $header . '.' . $payload;
    $signature = _b64url_encode(hash_hmac('sha256', $signing, $secret, true));
    return $signing . '.' . $signature;
}

/**
 * Encode a refresh JWT. Has a longer expiry (2 days by default) and a `type`
 * claim set to "refresh" so endpoints can distinguish it from an access token.
 */
function jwt_encode_refresh(string $subject, string $secret): string {
    $refreshDays = defined('JWT_REFRESH_EXPIRY_DAYS') ? JWT_REFRESH_EXPIRY_DAYS : 2;
    $header  = _b64url_encode(json_encode(['typ' => 'JWT', 'alg' => 'HS256']));
    $payload = _b64url_encode(json_encode([
        'iss'   => JWT_ISSUER,
        'aud'   => JWT_AUDIENCE,
        'sub'   => $subject,
        'type'  => 'refresh',
        'iat'   => time(),
        'exp'   => time() + $refreshDays * 24 * 3600,
    ]));
    $signing   = $header . '.' . $payload;
    $signature = _b64url_encode(hash_hmac('sha256', $signing, $secret, true));
    return $signing . '.' . $signature;
}

/**
 * Decode and validate a JWT. Returns the payload array on success, null on
 * any failure (bad signature, wrong alg, expired, wrong issuer/audience).
 */
function jwt_decode(string $token, string $secret): ?array {
    $parts = explode('.', $token);
    if (count($parts) !== 3) return null;
    [$headerB64, $payloadB64, $signatureB64] = $parts;

    $expected = _b64url_encode(hash_hmac('sha256', $headerB64 . '.' . $payloadB64, $secret, true));
    if (!hash_equals($expected, $signatureB64)) return null;

    $header = json_decode(_b64url_decode($headerB64), true);
    if (!is_array($header) || ($header['alg'] ?? '') !== 'HS256') return null;

    $payload = json_decode(_b64url_decode($payloadB64), true);
    if (!is_array($payload)) return null;

    if (!isset($payload['exp']) || $payload['exp'] < time())  return null;
    if (($payload['iss'] ?? '') !== JWT_ISSUER)               return null;
    if (($payload['aud'] ?? '') !== JWT_AUDIENCE)             return null;

    return $payload;
}

<?php

/**
 * Long-lived personal access tokens (`qm_pat_…`) that let an agent or CLI call the
 * API without a user's password. Mirrors the project-API-key storage contract: only
 * a sha256 hash is ever persisted, the plaintext token is returned once at creation.
 */

/** Comma-joins scope slugs for storage. */
function encodeAccessTokenScopes(array $scopes): string {
    return implode(',', $scopes);
}

/** Splits a stored scopes string back into a list, trimming whitespace and blanks. */
function decodeAccessTokenScopes(string $raw): array {
    return array_values(array_filter(array_map('trim', explode(',', $raw)), fn($s) => $s !== ''));
}

/** True if [scopesCsv] (as stored) grants [scope]. */
function hasAccessTokenScope(string $scopesCsv, string $scope): bool {
    return in_array($scope, decodeAccessTokenScopes($scopesCsv), true);
}

/**
 * Mints a fresh `qm_pat_…` token. Returns the plaintext (shown once by the caller),
 * its sha256 hash (what gets stored), and its last 4 chars (for masked display).
 */
function mintAccessToken(): array {
    $token = 'qm_pat_' . bin2hex(random_bytes(32));
    return [
        'token'     => $token,
        'tokenHash' => hash('sha256', $token),
        'last4'     => substr($token, -4),
    ];
}

/** True if [expiresAt] (ISO-8601 UTC, from now()) is in the past. Null never expires. */
function accessTokenIsExpired(?string $expiresAt): bool {
    if ($expiresAt === null) return false;
    return strtotime($expiresAt) < time();
}

/**
 * Inserts a new access token row for [userId] and returns the plaintext token plus
 * metadata — same shape mintAccessToken() returns, plus id/createdAt/expiresAt.
 */
function createAccessToken(string $userId, string $name, array $scopes, ?int $expiresInDays = null): array {
    $minted = mintAccessToken();
    $id = uuid4();
    $createdAt = now();
    $expiresAt = $expiresInDays !== null
        ? gmdate('Y-m-d\TH:i:s\Z', strtotime($createdAt) + $expiresInDays * 86400)
        : null;

    getDb()->prepare(
        'INSERT INTO access_tokens (id, user_id, name, token_hash, token_last4, scopes, created_at, expires_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
    )->execute([$id, $userId, $name, $minted['tokenHash'], $minted['last4'], encodeAccessTokenScopes($scopes), $createdAt, $expiresAt]);

    return [
        'id'        => $id,
        'token'     => $minted['token'],
        'last4'     => $minted['last4'],
        'name'      => $name,
        'scopes'    => $scopes,
        'createdAt' => $createdAt,
        'expiresAt' => $expiresAt,
    ];
}

/**
 * Resolves a plaintext token to its owning user id and scopes, or null if unknown,
 * revoked, or expired. Advances last_used_at on success.
 */
function validateAccessToken(string $token): ?array {
    $hash = hash('sha256', $token);
    $stmt = getDb()->prepare(
        'SELECT id, user_id, scopes, expires_at FROM access_tokens
         WHERE token_hash = ? AND revoked_at IS NULL LIMIT 1'
    );
    $stmt->execute([$hash]);
    $row = $stmt->fetch();
    if ($row === false) return null;
    if (accessTokenIsExpired($row['expires_at'])) return null;

    getDb()->prepare('UPDATE access_tokens SET last_used_at = ? WHERE id = ?')
        ->execute([now(), $row['id']]);

    return [
        'userId' => $row['user_id'],
        'scopes' => decodeAccessTokenScopes($row['scopes']),
    ];
}

/** Revokes a token. Returns false if it does not exist or is not owned by [userId]. */
function revokeAccessToken(string $id, string $userId): bool {
    $stmt = getDb()->prepare(
        'UPDATE access_tokens SET revoked_at = ? WHERE id = ? AND user_id = ? AND revoked_at IS NULL'
    );
    $stmt->execute([now(), $id, $userId]);
    return $stmt->rowCount() > 0;
}

/**
 * Reshapes one access_tokens row for the API/wire: decodes the stored comma-separated
 * `scopes` string into a list. Pulled out as its own pure function (rather than inlined in
 * listAccessTokens()) so it's unit-testable without a database — array `+` union keeps the
 * LEFT side's value on key collision, so `$row + ['scopes' => ...]` silently discards the
 * override and leaves 'scopes' as the raw string; that bug shipped past the pure-function
 * test suite once already because nothing here exercised it end-to-end.
 */
function shapeAccessTokenRow(array $row): array {
    $row['scopes'] = decodeAccessTokenScopes($row['scopes']);
    return $row;
}

/** Non-revoked tokens owned by [userId], newest first. Never includes the plaintext token. */
function listAccessTokens(string $userId): array {
    $stmt = getDb()->prepare(
        'SELECT id, name, token_last4 AS last4, scopes, created_at, expires_at, last_used_at
         FROM access_tokens WHERE user_id = ? AND revoked_at IS NULL ORDER BY created_at DESC'
    );
    $stmt->execute([$userId]);
    return array_map('shapeAccessTokenRow', $stmt->fetchAll());
}

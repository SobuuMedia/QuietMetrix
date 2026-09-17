<?php

/**
 * Personal access tokens (`qm_pat_…`) for agents/CLIs — see docs/agents/setup.md.
 * Always dashboard-session (JWT) authenticated: a token can never mint, list, or
 * revoke another token.
 */

const ACCESS_TOKEN_ALLOWED_SCOPES = ['projects:create', 'projects:read', 'analytics:read'];

/** POST /api/v1/tokens { name, scopes?, expires_in_days? } */
function handleTokensCreate(): void {
    $session = requireSession();
    $body = getJsonBody();
    $name = trim((string)($body['name'] ?? ''));
    if ($name === '') {
        errorResponse(400, 'schema_violation', "Field 'name' is required and must be a non-empty string");
        return;
    }

    $scopes = $body['scopes'] ?? null;
    if (!is_array($scopes) || count($scopes) === 0) {
        $scopes = ['projects:create'];
    }
    $scopes = array_values(array_unique(array_map('strval', $scopes)));
    $unknown = array_diff($scopes, ACCESS_TOKEN_ALLOWED_SCOPES);
    if (!empty($unknown)) {
        errorResponse(400, 'schema_violation', 'Unknown scope(s): ' . implode(', ', $unknown));
        return;
    }

    // projects:create is the highest-privilege scope a PAT can carry — only an admin
    // session may mint one, mirroring the POST /projects admin gate.
    if (in_array('projects:create', $scopes, true) && ($session['role'] ?? '') !== 'admin') {
        errorResponse(403, 'forbidden', 'Only admins can create tokens with the projects:create scope');
        return;
    }

    $expiresInDays = isset($body['expires_in_days']) && $body['expires_in_days'] !== null
        ? (int)$body['expires_in_days']
        : null;

    $created = createAccessToken((string)$session['sub'], $name, $scopes, $expiresInDays);

    jsonResponse(201, [
        'id'         => $created['id'],
        'name'       => $created['name'],
        'token'      => $created['token'],
        'last4'      => $created['last4'],
        'scopes'     => $created['scopes'],
        'created_at' => $created['createdAt'],
        'expires_at' => $created['expiresAt'],
    ]);
}

/** GET /api/v1/tokens → non-revoked tokens owned by the caller, newest first. */
function handleTokensList(): void {
    $session = requireSession();
    $tokens = listAccessTokens((string)$session['sub']);
    jsonResponse(200, ['tokens' => array_map(fn($t) => [
        'id'           => $t['id'],
        'name'         => $t['name'],
        'last4'        => $t['last4'],
        'scopes'       => $t['scopes'],
        'created_at'   => $t['created_at'],
        'expires_at'   => $t['expires_at'],
        'last_used_at' => $t['last_used_at'],
    ], $tokens)]);
}

/** DELETE /api/v1/tokens/{id} */
function handleTokensDelete(string $tokenId): void {
    $session = requireSession();
    if (!revokeAccessToken($tokenId, (string)$session['sub'])) {
        errorResponse(404, 'not_found', 'Token not found');
        return;
    }
    jsonResponse(200, ['revoked' => true]);
}

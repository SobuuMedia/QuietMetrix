<?php

/** POST /api/v1/auth/login → { token, refresh_token, user } */
function handleLogin(): void {
    $body  = getJsonBody();
    $email = trim((string)($body['email']    ?? ''));
    $pass  = (string)        ($body['password'] ?? '');
    if ($email === '' || $pass === '') {
        errorResponse(400, 'invalid_request', 'email and password are required');
        return;
    }

    $stmt = getDb()->prepare(
        'SELECT id, email, password_hash, role FROM users WHERE email = ? LIMIT 1'
    );
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    if ($user === false || !password_verify($pass, $user['password_hash'])) {
        // Same response for "no such user" and "wrong password" — don't leak which.
        errorResponse(401, 'unauthorized', 'Invalid credentials');
        return;
    }

    $token        = jwt_encode($user['id'], $user['email'], $user['role'], JWT_SECRET);
    $refreshToken = jwt_encode_refresh($user['id'], JWT_SECRET);
    jsonResponse(200, [
        'token'         => $token,
        'refresh_token' => $refreshToken,
        'user'  => [
            'id'    => $user['id'],
            'email' => $user['email'],
            'role'  => $user['role'],
        ],
    ]);
}

/** POST /api/v1/auth/refresh → { token, refresh_token } */
function handleRefresh(): void {
    $body = getJsonBody();
    $refreshToken = trim((string)($body['refresh_token'] ?? ''));
    if ($refreshToken === '') {
        errorResponse(400, 'invalid_request', 'refresh_token is required');
        return;
    }

    $payload = jwt_decode($refreshToken, JWT_SECRET);
    if ($payload === null || ($payload['type'] ?? '') !== 'refresh') {
        errorResponse(401, 'unauthorized', 'Invalid or expired refresh token');
        return;
    }

    // Verify the user still exists
    $stmt = getDb()->prepare('SELECT id, email, role FROM users WHERE id = ? LIMIT 1');
    $stmt->execute([$payload['sub']]);
    $user = $stmt->fetch();
    if ($user === false) {
        errorResponse(401, 'unauthorized', 'User no longer exists');
        return;
    }

    // Rotate both tokens
    $newToken        = jwt_encode($user['id'], $user['email'], $user['role'], JWT_SECRET);
    $newRefreshToken = jwt_encode_refresh($user['id'], JWT_SECRET);

    jsonResponse(200, [
        'token'         => $newToken,
        'refresh_token' => $newRefreshToken,
    ]);
}

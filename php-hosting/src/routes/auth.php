<?php

/** POST /api/v1/auth/login → { token, refresh_token, user } */
function handleLogin(): void {
    enforceSharedLimit("login-ip:" . (clientIp() ?? "unknown"), 20);
    $body  = getJsonBody();
    $email = trim((string)($body['email']    ?? ''));
    $pass  = (string)        ($body['password'] ?? '');
    if ($email === '' || $pass === '') {
        errorResponse(400, 'invalid_request', 'email and password are required');
        return;
    }

    enforceSharedLimit('login-account:' . strtolower($email), 5);
    $stmt = getDb()->prepare(
        'SELECT id, email, password_hash, role, status, platform_operator FROM users WHERE email = ? LIMIT 1'
    );
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    if ($user === false || $user['status'] !== 'active' || !password_verify($pass, $user['password_hash'])) {
        // Same response for "no such user" and "wrong password" — don't leak which.
        errorResponse(401, 'unauthorized', 'Invalid credentials');
        return;
    }

    $sessionId = bin2hex(random_bytes(16));
    $token = jwt_encode($user['id'], $user['email'], $user['role'], JWT_SECRET, $sessionId);
    $refreshToken = jwt_encode_refresh($user['id'], JWT_SECRET, $sessionId);
    $db = getDb();
    $db->beginTransaction();
    try {
        // Serialize session creation with password recovery. A password verified
        // before a reset must never mint a new session after that reset.
        $current = $db->prepare('SELECT password_hash,status FROM users WHERE id=? FOR UPDATE');
        $current->execute([$user['id']]);
        $locked = $current->fetch();
        if (!$locked || $locked['status'] !== 'active' || !hash_equals($user['password_hash'], $locked['password_hash'])) {
            $db->rollBack();
            errorResponse(401, 'unauthorized', 'Invalid credentials');
            return;
        }
        $db->prepare('DELETE FROM auth_sessions WHERE expires_at <= ?')->execute([time()]);
        $db->prepare('INSERT INTO auth_sessions (id,user_id,refresh_hash,expires_at) VALUES (?,?,?,?)')->execute([$sessionId,$user['id'],hash('sha256',$refreshToken),time()+172800]);
        $db->commit();
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        throw $e;
    }
    jsonResponse(200, [
        'token'         => $token,
        'refresh_token' => $refreshToken,
        'user'  => [
            'id'    => $user['id'],
            'email' => $user['email'],
            'role'  => $user['role'],
            'platform_operator' => (bool)$user['platform_operator'],
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
    $stmt = getDb()->prepare('SELECT id, email, role, status FROM users WHERE id = ? LIMIT 1');
    $stmt->execute([$payload['sub']]);
    $user = $stmt->fetch();
    if ($user === false || $user['status'] !== 'active') {
        errorResponse(401, 'unauthorized', 'User no longer exists');
        return;
    }

    // Rotate both tokens
    $sessionId = $payload['sessionId'] ?? '';
    $newRefreshToken = jwt_encode_refresh($user['id'], JWT_SECRET, $sessionId);
    $rotate = getDb()->prepare('UPDATE auth_sessions SET refresh_hash=?, expires_at=? WHERE id=? AND user_id=? AND refresh_hash=? AND expires_at>?');
    $rotate->execute([hash('sha256',$newRefreshToken),time()+172800,$sessionId,$user['id'],hash('sha256',$refreshToken),time()]);
    if ($rotate->rowCount() !== 1) { errorResponse(401, 'unauthorized', 'Refresh token was revoked or already used'); return; }
    $newToken = jwt_encode($user['id'], $user['email'], $user['role'], JWT_SECRET, $sessionId);

    jsonResponse(200, [
        'token'         => $newToken,
        'refresh_token' => $newRefreshToken,
    ]);
}

function handleLogout(): void {
    $refresh = (string)(getJsonBody()['refresh_token'] ?? '');
    $payload = jwt_decode($refresh, JWT_SECRET);
    if ($payload && ($payload['type'] ?? '') === 'refresh') {
        getDb()->prepare('DELETE FROM auth_sessions WHERE id=? AND refresh_hash=?')->execute([$payload['sessionId'] ?? '',hash('sha256',$refresh)]);
    }
    http_response_code(204);
}

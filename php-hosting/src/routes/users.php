<?php

// User management + invitation flow. All /users endpoints are admin-only; the
// /invites endpoints are public (a new user accepts via a tokenised link).

const VALID_ROLES = ['admin', 'developer', 'reviewer'];

/** GET /api/v1/users → list all users (admin only). */
function handleUsersList(): void {
    $session = requireSession();
    requireAdmin($session);

    $stmt = getDb()->query('SELECT id, email, role, status, created_at FROM users ORDER BY created_at DESC');
    jsonResponse(200, ['users' => $stmt->fetchAll()]);
}

/**
 * POST /api/v1/users/invite { email, role } → creates an invited user and
 * returns an invitation link. Sends an email too if INVITE_EMAIL_ENABLED.
 */
function handleUserInvite(): void {
    $session = requireSession();
    requireAdmin($session);

    $body  = getJsonBody();
    $email = strtolower(trim((string)($body['email'] ?? '')));
    $role  = (string)($body['role'] ?? '');
    if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        errorResponse(400, 'invalid_request', 'A valid email is required');
        return;
    }
    if (!in_array($role, VALID_ROLES, true)) {
        errorResponse(400, 'invalid_request', 'role must be one of: ' . implode(', ', VALID_ROLES));
        return;
    }

    $stmt = getDb()->prepare('SELECT id FROM users WHERE email = ? LIMIT 1');
    $stmt->execute([$email]);
    if ($stmt->fetch() !== false) {
        errorResponse(409, 'already_exists', 'A user with that email already exists');
        return;
    }

    $id      = uuid4();
    $token   = randomToken();
    $expires = gmdate('Y-m-d\TH:i:s\Z', time() + INVITE_EXPIRY_DAYS * 86400);

    getDb()->prepare(
        'INSERT INTO users (id, email, password_hash, role, status, invite_token, invite_expires, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
    )->execute([$id, $email, '!invited', $role, 'invited', tokenHash($token), $expires, now()]);

    $link = inviteLink($token);
    if (defined('INVITE_EMAIL_ENABLED') && INVITE_EMAIL_ENABLED) {
        sendInviteEmail($email, $link);
    }

    jsonResponse(201, [
        'invite_link' => $link,
        'user' => ['id' => $id, 'email' => $email, 'role' => $role, 'status' => 'invited'],
    ]);
}

/** PATCH /api/v1/users/{id} { role } → change a user's global role (admin only). */
function handleUserUpdateRole(string $userId): void {
    $session = requireSession();
    requireAdmin($session);

    $body = getJsonBody();
    $role = (string)($body['role'] ?? '');
    if (!in_array($role, VALID_ROLES, true)) {
        errorResponse(400, 'invalid_request', 'role must be one of: ' . implode(', ', VALID_ROLES));
        return;
    }

    $stmt = getDb()->prepare('SELECT role FROM users WHERE id = ? LIMIT 1');
    $stmt->execute([$userId]);
    $user = $stmt->fetch();
    if ($user === false) {
        errorResponse(404, 'not_found', 'User not found');
        return;
    }
    // Don't allow demoting the last remaining admin.
    if ($user['role'] === 'admin' && $role !== 'admin' && countAdmins() <= 1) {
        errorResponse(400, 'last_admin', 'Cannot remove the last admin');
        return;
    }

    getDb()->prepare('UPDATE users SET role = ? WHERE id = ?')->execute([$role, $userId]);
    jsonResponse(200, ['ok' => true]);
}

/** DELETE /api/v1/users/{id} → remove a user (admin only). */
function handleUserDelete(string $userId): void {
    $session = requireSession();
    requireAdmin($session);

    if ($userId === ($session['sub'] ?? '')) {
        errorResponse(400, 'invalid_request', 'You cannot delete your own account');
        return;
    }
    $stmt = getDb()->prepare('SELECT role FROM users WHERE id = ? LIMIT 1');
    $stmt->execute([$userId]);
    $user = $stmt->fetch();
    if ($user === false) {
        errorResponse(404, 'not_found', 'User not found');
        return;
    }
    if ($user['role'] === 'admin' && countAdmins() <= 1) {
        errorResponse(400, 'last_admin', 'Cannot delete the last admin');
        return;
    }

    getDb()->prepare('DELETE FROM users WHERE id = ?')->execute([$userId]);
    jsonResponse(200, ['ok' => true]);
}

/** GET /api/v1/invites/{token} → validate an invite, returns the invitee email. Public. */
function handleInviteGet(string $token): void {
    $user = findValidInvite($token);
    if ($user === null) {
        errorResponse(404, 'invalid_invite', 'This invitation is invalid or has expired');
        return;
    }
    jsonResponse(200, ['email' => $user['email']]);
}

/**
 * POST /api/v1/invites/{token}/accept { password } → sets the password, activates
 * the account, and returns login tokens (auto sign-in). Public.
 */
function handleInviteAccept(string $token): void {
    $body = getJsonBody();
    $pass = (string)($body['password'] ?? '');
    if (strlen($pass) < 8) {
        errorResponse(400, 'invalid_request', 'Password must be at least 8 characters');
        return;
    }

    $user = findValidInvite($token);
    if ($user === null) {
        errorResponse(404, 'invalid_invite', 'This invitation is invalid or has expired');
        return;
    }

    getDb()->prepare(
        'UPDATE users SET password_hash = ?, status = ?, invite_token = NULL, invite_expires = NULL WHERE id = ?'
    )->execute([password_hash($pass, PASSWORD_BCRYPT, ['cost' => 10]), 'active', $user['id']]);

    $jwt     = jwt_encode($user['id'], $user['email'], $user['role'], JWT_SECRET);
    $refresh = jwt_encode_refresh($user['id'], JWT_SECRET);
    jsonResponse(200, [
        'token'         => $jwt,
        'refresh_token' => $refresh,
        'user'  => ['id' => $user['id'], 'email' => $user['email'], 'role' => $user['role']],
    ]);
}

// --- helpers ---------------------------------------------------------------

/** Looks up a user by a still-valid (unexpired, invited) invite token. */
function findValidInvite(string $token): ?array {
    $stmt = getDb()->prepare(
        "SELECT id, email, role FROM users
          WHERE invite_token = ? AND status = 'invited' AND invite_expires > ? LIMIT 1"
    );
    $stmt->execute([tokenHash($token), now()]);
    $user = $stmt->fetch();
    return $user === false ? null : $user;
}

function countAdmins(): int {
    return (int)getDb()->query("SELECT COUNT(*) FROM users WHERE role = 'admin'")->fetchColumn();
}

/** Builds the dashboard invitation link for a raw (unhashed) token. */
function inviteLink(string $token): string {
    $base = defined('APP_BASE_URL') && APP_BASE_URL !== '' ? APP_BASE_URL : requestOrigin() . '/dashboard/';
    return rtrim($base, '/') . '/?invite=' . $token;
}

/** Best-effort scheme+host of the current request, for fallback invite links. */
function requestOrigin(): string {
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host   = $_SERVER['HTTP_HOST'] ?? 'localhost';
    return $scheme . '://' . $host;
}

function sendInviteEmail(string $to, string $link): void {
    $from    = defined('INVITE_FROM_EMAIL') ? INVITE_FROM_EMAIL : 'no-reply@localhost';
    $subject = 'You have been invited to QuietMetrix';
    $message = "You have been invited to QuietMetrix.\n\n"
             . "Open this link to set your password and sign in:\n$link\n";
    $headers = "From: $from\r\nContent-Type: text/plain; charset=UTF-8\r\n";
    @mail($to, $subject, $message, $headers);
}

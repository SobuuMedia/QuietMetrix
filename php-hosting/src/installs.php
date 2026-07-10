<?php

/**
 * Abuse-defense Stage 2 — per-install (anonymousId) tracking + ramp-up detector.
 *
 * The SDK sends `anonymousId` raw on the wire; the server NEVER stores or logs it raw.
 * It is salt-hashed (per-project `install_salt`) before landing in `install_meta`.
 * The salt is rotated on API-key regeneration, which discards old install rows (they
 * cannot be recomputed against the new salt). See docs/security/publishable-api-key.md
 * — Privacy note.
 */

/** Per-project install salt. */
function getInstallSalt(string $projectId): ?string {
    $stmt = getDb()->prepare('SELECT install_salt FROM projects WHERE id = ? LIMIT 1');
    $stmt->execute([$projectId]);
    $row = $stmt->fetch();
    return is_array($row) && !empty($row['install_salt']) ? $row['install_salt'] : null;
}

/** Salted SHA-256 hex of an install id. Never log the raw id. */
function hashInstallId(string $salt, string $anonymousId): string {
    return hash('sha256', $salt . ':' . $anonymousId);
}

/**
 * Insert-or-increment an install by its hashed id. Auto-revokes when [delta] events
 * push the install over the ramp threshold within the ramp window. Returns
 * ['revoked' => bool, 'event_count' => int].
 */
function upsertInstall(string $projectId, string $installHash, int $delta = 1): array {
    $db = getDb();
    $now = now();
    $threshold = defined('INGEST_INSTALL_RAMP_EVENTS') ? (int)INGEST_INSTALL_RAMP_EVENTS : 500;
    $windowMin = defined('INGEST_INSTALL_RAMP_MINUTES') ? (int)INGEST_INSTALL_RAMP_MINUTES : 10;

    $stmt = $db->prepare(
        'SELECT first_seen_at, event_count, revoked FROM install_meta
          WHERE project_id = ? AND anonymous_id_hash = ? LIMIT 1'
    );
    $stmt->execute([$projectId, $installHash]);
    $row = $stmt->fetch();

    if ($row === false) {
        $revoked = ($delta >= $threshold) ? 1 : 0;
        $db->prepare(
            'INSERT INTO install_meta (project_id, anonymous_id_hash, first_seen_at, last_seen_at, event_count, revoked)
             VALUES (?, ?, ?, ?, ?, ?)'
        )->execute([$projectId, $installHash, $now, $now, $delta, $revoked]);
        return ['revoked' => (bool)$revoked, 'event_count' => $delta];
    }

    $firstSeen = $row['first_seen_at'];
    $newCount = (int)$row['event_count'] + $delta;
    $withinWindow = (strtotime($now) - strtotime($firstSeen)) < ($windowMin * 60);
    $shouldRevoke = (!$row['revoked']) && $withinWindow && ($newCount >= $threshold);
    $revoked = $shouldRevoke ? 1 : (int)$row['revoked'];
    $db->prepare(
        'UPDATE install_meta SET last_seen_at = ?, event_count = ?, revoked = ?
          WHERE project_id = ? AND anonymous_id_hash = ?'
    )->execute([$now, $newCount, $revoked, $projectId, $installHash]);
    return ['revoked' => (bool)$revoked, 'event_count' => $newCount];
}

/** Manually revoke an install by its hashed id. Returns true if a row was affected. */
function revokeInstall(string $projectId, string $installHash): bool {
    $stmt = getDb()->prepare(
        'UPDATE install_meta SET revoked = 1 WHERE project_id = ? AND anonymous_id_hash = ?'
    );
    $stmt->execute([$projectId, $installHash]);
    return $stmt->rowCount() > 0;
}

/** Delete all install rows for a project (e.g. on key rotation). */
function deleteInstallsForProject(string $projectId): int {
    $stmt = getDb()->prepare('DELETE FROM install_meta WHERE project_id = ?');
    $stmt->execute([$projectId]);
    return $stmt->rowCount();
}

/**
 * Apply the per-install ramp-up + revoke check for one event's anonymousId.
 * Returns null if the event may proceed, or an error string to surface.
 */
function enforceInstallLimits(string $projectId, ?string $anonymousId): ?string {
    if (!defined('INGEST_INSTALL_ENABLED') || !INGEST_INSTALL_ENABLED) return null;
    if ($anonymousId === null || $anonymousId === '') return null;

    $salt = getInstallSalt($projectId);
    if ($salt === null) return null;

    $hash = hashInstallId($salt, $anonymousId);
    $install = upsertInstall($projectId, $hash, 1);
    if (!empty($install['revoked'])) {
        // Audit the revoked attempt. The caller will insert the full event into quarantine
        // and respond 403 (handled in track.php). See docs/security/publishable-api-key.md.
        if (function_exists('auditLog')) {
            auditLog($projectId, '', null, $hash, '?', 'quarantine', 'auto-revoked ramp-up');
        }
        errorResponse(403, 'install_revoked', 'Install has been revoked due to anomalous activity.');
        return 'install_revoked';
    }
    // Per-install token bucket (APCu). Burst defaults to 30.
    $burst = defined('INGEST_INSTALL_BURST') ? (int)INGEST_INSTALL_BURST : 30;
    $rps = defined('INGEST_INSTALL_RPS') ? (int)INGEST_INSTALL_RPS : 1;
    enforceLimit("track:install:$projectId:$hash", $rps, $burst);
    return null;
}
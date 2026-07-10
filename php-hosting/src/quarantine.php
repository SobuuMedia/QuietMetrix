<?php

/**
 * Abuse-defense Stage 4 — quarantine + audit.
 * See docs/security/publishable-api-key.md.
 */

/** Insert a quarantined event (serialised payload). Returns the new row id. */
function quarantineEvent(string $projectId, array $event, string $reason, string $detail,
                         ?string $clientIp, ?string $anonymousIdHash): void {
    $payload = json_encode($event, JSON_UNESCAPED_UNICODE);
    getDb()->prepare(
        'INSERT INTO events_quarantine (project_id, payload, quarantine_reason, quarantine_detail, client_ip, anonymous_id_hash, quarantined_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)'
    )->execute([$projectId, $payload, $reason, $detail, $clientIp, $anonymousIdHash, now()]);
}

/** Log an ingest audit entry. */
function auditLog(string $projectId, string $apiKey, ?string $clientIp, ?string $anonymousIdHash,
                  string $eventName, string $disposition, ?string $reason): void {
    $last4 = strlen($apiKey) >= 4 ? substr($apiKey, -4) : null;
    getDb()->prepare(
        'INSERT INTO ingest_audit (project_id, api_key_last4, client_ip, anonymous_id_hash, event_name, disposition, reason, audited_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
    )->execute([$projectId, $last4, $clientIp, $anonymousIdHash, $eventName, $disposition, $reason, now()]);
}

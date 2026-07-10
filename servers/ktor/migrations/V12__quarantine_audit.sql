-- Abuse-defense Stage 4: quarantine table for events flagged by heuristic checks
-- (ramp-up, schema violation, etc.), and ingest audit log. See docs/security/publishable-api-key.md.
CREATE TABLE IF NOT EXISTS events_quarantine (
    id                 BIGSERIAL    PRIMARY KEY,
    project_id         BIGINT       NOT NULL,
    payload            TEXT         NOT NULL,
    quarantine_reason  VARCHAR(50)  NOT NULL,
    quarantine_detail  TEXT,
    client_ip          VARCHAR(45),
    anonymous_id_hash  VARCHAR(64),
    quarantined_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_q_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_quarantine_project_time ON events_quarantine (project_id, quarantined_at);

CREATE TABLE IF NOT EXISTS ingest_audit (
    id                 BIGSERIAL    PRIMARY KEY,
    project_id         BIGINT       NOT NULL,
    api_key_last4      VARCHAR(4),
    client_ip          VARCHAR(45),
    anonymous_id_hash  VARCHAR(64),
    event_name         VARCHAR(255) NOT NULL,
    disposition        VARCHAR(20)  NOT NULL,
    reason             TEXT,
    audited_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_audit_project_time ON ingest_audit (project_id, audited_at);
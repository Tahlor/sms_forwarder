-- Initial schema for SMS Bridge multi-device server
CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    label TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    last_seen_at TEXT,
    revoked_at TEXT,
    metadata TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS pairing_tokens (
    token TEXT PRIMARY KEY,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    expires_at TEXT NOT NULL,
    claimed_at TEXT,
    device_id TEXT REFERENCES devices(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    direction TEXT NOT NULL CHECK (direction IN ('inbound', 'outbound')),
    correspondent TEXT NOT NULL,
    body TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    is_security_code INTEGER NOT NULL DEFAULT 0,
    extracted_code TEXT,
    client_fingerprint TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(device_id, client_fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_messages_thread 
    ON messages(device_id, correspondent, timestamp ASC);

CREATE INDEX IF NOT EXISTS idx_messages_timestamp 
    ON messages(timestamp DESC);

CREATE TABLE IF NOT EXISTS outbound_jobs (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    recipient TEXT NOT NULL,
    body TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'claimed', 'completed', 'failed')),
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    ack_details TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbound_jobs_pending 
    ON outbound_jobs(device_id, status);

CREATE TABLE IF NOT EXISTS notification_jobs (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    priority TEXT NOT NULL DEFAULT 'normal' CHECK (priority IN ('low', 'normal', 'high')),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'completed')),
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    ack_details TEXT
);

CREATE INDEX IF NOT EXISTS idx_notification_jobs_pending 
    ON notification_jobs(device_id, status);

CREATE TABLE IF NOT EXISTS audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor_type TEXT NOT NULL CHECK (actor_type IN ('admin', 'device', 'system')),
    actor_id TEXT,
    action TEXT NOT NULL,
    target_id TEXT,
    details TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

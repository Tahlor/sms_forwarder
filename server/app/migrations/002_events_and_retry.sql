-- Migration 002: Add device_events table and retry support for outbound & notification jobs

CREATE TABLE IF NOT EXISTS device_events (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL DEFAULT '{}',
    timestamp INTEGER NOT NULL,
    client_fingerprint TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(device_id, client_fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_device_events_timestamp 
    ON device_events(device_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_device_events_type 
    ON device_events(device_id, event_type);

ALTER TABLE outbound_jobs ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE notification_jobs ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;

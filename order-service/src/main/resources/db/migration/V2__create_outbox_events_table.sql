CREATE TABLE outbox_events (
    id                UUID PRIMARY KEY,
    aggregate_type    VARCHAR(255) NOT NULL,
    aggregate_id      VARCHAR(255) NOT NULL,
    event_type        VARCHAR(255) NOT NULL,
    payload           TEXT NOT NULL,
    status            VARCHAR(32) NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    publish_attempts  INTEGER NOT NULL DEFAULT 0,
    published_at      TIMESTAMPTZ
);

-- Partial index: the relay only ever scans PENDING rows, so only those need to be indexed.
-- With a single relay replica this is a nice-to-have, not a hard requirement (see README —
-- "Fuera de alcance" covers what multi-replica would additionally need).
CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at)
    WHERE status = 'PENDING';

-- event_id is the primary key on purpose: this constraint IS the idempotency guarantee, not
-- just an index. See ProcessedEventJpaRepository#tryInsert for how it's used without ever
-- throwing a constraint-violation exception into the enclosing transaction.
CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL
);

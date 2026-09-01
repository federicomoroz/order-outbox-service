-- Exponential backoff for the outbox relay.
--
-- Before this migration a row that failed MAX_PUBLISH_ATTEMPTS times was FAILED forever and
-- needed a human. Re-publishing is safe (the consumer downstream is idempotent), so FAILED
-- becomes a soft, self-healing state instead: the relay keeps retrying it, just further and
-- further apart. This column is where "further apart" is recorded.

ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

-- No DEFAULT and no backfill UPDATE, on purpose. NULL means "due now" (see OutboxEvent.isDueAt):
-- that is right for brand-new rows, which have never been attempted, and it is also exactly what
-- pre-existing rows want. Rows already PUBLISHED are never selected by the relay, so their NULL
-- is inert; rows left FAILED under the old terminal semantics become due on the very next poll,
-- which is precisely the self-healing this migration introduces — the backlog gets picked up
-- rather than staying dead. A DEFAULT now() would instead have silently delayed every existing
-- row, and a NOT NULL column would have forced a rewrite of the whole table for no gain.

-- The relay's query changed shape with the column, so the index that backed it has to follow.
-- It no longer looks for status = 'PENDING'; it looks for everything still awaiting relay. A
-- partial index on the old predicate would simply stop being used.
DROP INDEX idx_outbox_events_pending;

-- Partial on status only: the `next_attempt_at <= now()` half of the query is not immutable and
-- cannot live in an index predicate, so it stays a cheap filter over the (small) set of rows this
-- index already narrows things down to. occurred_at is the indexed column because the relay reads
-- oldest-first, which lets the ORDER BY be answered by the index instead of a sort.
CREATE INDEX idx_outbox_events_due ON outbox_events (occurred_at)
    WHERE status IN ('PENDING', 'FAILED');

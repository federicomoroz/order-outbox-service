CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL,
    customer_id  UUID NOT NULL,
    message      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

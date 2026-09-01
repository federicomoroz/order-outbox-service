CREATE TABLE orders (
    id                   UUID PRIMARY KEY,
    customer_id          UUID NOT NULL,
    product_id           VARCHAR(255) NOT NULL,
    quantity             INTEGER NOT NULL,
    unit_price_amount    NUMERIC(19, 2) NOT NULL,
    unit_price_currency  VARCHAR(3) NOT NULL,
    status               VARCHAR(32) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL
);

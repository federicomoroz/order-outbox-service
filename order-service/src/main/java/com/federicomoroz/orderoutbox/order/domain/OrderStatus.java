package com.federicomoroz.orderoutbox.order.domain;

/**
 * Lifecycle status of an {@link Order}.
 *
 * <p>Only {@link #CREATED} exists today — deliberate minimalism, not an oversight. Nothing in
 * this service (or the outbox event it publishes) currently needs to distinguish further
 * states (e.g. CONFIRMED, SHIPPED, CANCELLED). Modeled as an enum rather than a boolean flag
 * so that adding a real lifecycle later is additive, not a breaking change to every call site.
 */
public enum OrderStatus {
    CREATED
}

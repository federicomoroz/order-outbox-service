package com.federicomoroz.orderoutbox.order.domain;

/** Lifecycle status of an {@link OutboxEvent}. */
public enum OutboxStatus {
    /** Written in the same transaction as the aggregate; not yet relayed to Kafka. */
    PENDING,
    /** Successfully handed off to the broker by the relay. */
    PUBLISHED,
    /** Exceeded {@link OutboxEvent#MAX_PUBLISH_ATTEMPTS}; the relay stops retrying it. */
    FAILED
}

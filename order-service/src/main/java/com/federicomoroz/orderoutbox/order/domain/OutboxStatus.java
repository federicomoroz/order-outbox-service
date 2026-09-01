package com.federicomoroz.orderoutbox.order.domain;

import java.util.Set;

/** Lifecycle status of an {@link OutboxEvent}. */
public enum OutboxStatus {
    /** Written in the same transaction as the aggregate; not yet relayed to Kafka. */
    PENDING,
    /** Successfully handed off to the broker by the relay. The only terminal status. */
    PUBLISHED,
    /**
     * Reached {@link OutboxEvent#MAX_PUBLISH_ATTEMPTS} without an ack. A <em>soft</em> state — it
     * means "degraded, still being retried, look at me if I am old", not "abandoned": the relay
     * keeps picking it up at the capped {@link RetryBackoffPolicy} interval, forever, because
     * re-publishing is safe (the consumer downstream is idempotent).
     */
    FAILED;

    /**
     * The statuses the relay still has work to do on. Lives here rather than in the persistence
     * adapter's query so that "which rows are the relay's business" stays a domain decision; the
     * SQL only receives the answer.
     */
    public static Set<OutboxStatus> awaitingRelay() {
        return Set.of(PENDING, FAILED);
    }
}

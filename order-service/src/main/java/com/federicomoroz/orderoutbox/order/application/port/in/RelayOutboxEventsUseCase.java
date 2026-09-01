package com.federicomoroz.orderoutbox.order.application.port.in;

/**
 * Primary port for relaying outbox events to the broker. Driven by {@code OutboxRelayScheduler}
 * on a fixed delay, never by the HTTP path.
 */
public interface RelayOutboxEventsUseCase {

    /**
     * Relays every event that is <em>due</em> right now — not merely every {@code PENDING} one.
     * An event that failed recently is deliberately skipped until its backoff window elapses.
     */
    RelayOutcome relayDueEvents();

    /**
     * Summary of one relay run, returned so callers (tests, logs) can observe what happened
     * without re-querying the repository. {@code failedCount} counts events that came out of this
     * run flagged {@code FAILED}; they stay in the rotation and will be attempted again later.
     */
    record RelayOutcome(int publishedCount, int failedCount, int retriedCount) {
    }
}

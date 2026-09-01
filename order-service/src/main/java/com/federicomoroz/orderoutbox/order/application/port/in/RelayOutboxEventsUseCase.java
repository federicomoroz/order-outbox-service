package com.federicomoroz.orderoutbox.order.application.port.in;

/**
 * Primary port for relaying pending outbox events to the broker. Driven by
 * {@code OutboxRelayScheduler} on a fixed delay, never by the HTTP path.
 */
public interface RelayOutboxEventsUseCase {

    RelayOutcome relayPendingEvents();

    /**
     * Summary of one relay run, returned so callers (tests, logs) can observe what happened
     * without re-querying the repository.
     */
    record RelayOutcome(int publishedCount, int failedCount, int retriedCount) {
    }
}

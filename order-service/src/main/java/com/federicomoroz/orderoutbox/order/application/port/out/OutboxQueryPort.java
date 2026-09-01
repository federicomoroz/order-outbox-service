package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

import java.util.List;

/**
 * Read-only secondary port for observing the outbox, deliberately kept apart from
 * {@link OutboxRepository}.
 *
 * <p>Same Interface Segregation reasoning as {@link OrderQueryPort}: {@code OutboxRepository}
 * is shaped for the relay ({@code save} + {@code findDueBatch}) and only ever wants the rows it
 * may act on right now — never a {@code PUBLISHED} one, and not even a {@code FAILED} one whose
 * backoff has not elapsed. This port is shaped for an observer that wants the last N events
 * regardless of status or of whether they are due.
 */
public interface OutboxQueryPort {

    /** Most recently recorded outbox events first, at most {@code limit} of them. */
    List<OutboxEvent> findRecent(int limit);
}

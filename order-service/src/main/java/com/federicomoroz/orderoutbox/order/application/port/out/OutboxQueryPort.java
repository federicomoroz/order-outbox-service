package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

import java.util.List;

/**
 * Read-only secondary port for observing the outbox, deliberately kept apart from
 * {@link OutboxRepository}.
 *
 * <p>Same Interface Segregation reasoning as {@link OrderQueryPort}: {@code OutboxRepository}
 * is shaped for the relay ({@code save} + {@code findPendingBatch}) and only ever wants
 * {@code PENDING} rows. This port is shaped for an observer that wants the last N events
 * regardless of status — including the {@code PUBLISHED} and {@code FAILED} ones the relay has
 * no business reading back.
 */
public interface OutboxQueryPort {

    /** Most recently recorded outbox events first, at most {@code limit} of them. */
    List<OutboxEvent> findRecent(int limit);
}

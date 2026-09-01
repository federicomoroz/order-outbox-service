package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

import java.time.Instant;
import java.util.List;

/** Secondary port for persisting {@link OutboxEvent}s and reading back the batches to relay. */
public interface OutboxRepository {

    /**
     * Upsert: used both to insert a brand-new event and to persist a state transition the domain
     * already decided. The adapter writes what it is handed — it never computes attempt counts,
     * statuses or retry deadlines of its own.
     */
    void save(OutboxEvent event);

    /**
     * Reads up to {@code batchSize} events the relay may attempt at {@code now}, oldest first:
     * still awaiting relay ({@code PENDING} or {@code FAILED}) <em>and</em> past their backoff
     * window. Rows whose next attempt is still in the future are left alone.
     *
     * <p>{@code now} is a parameter rather than something the adapter reads off a clock, so the
     * relay's notion of time stays testable and lives on the application side of the boundary.
     */
    List<OutboxEvent> findDueBatch(int batchSize, Instant now);
}

package com.federicomoroz.orderoutbox.notification.application.port.out;

import com.federicomoroz.orderoutbox.notification.domain.ProcessedEvent;

/** Secondary port guarding idempotency. */
public interface ProcessedEventRepository {

    /**
     * Atomically attempts to record that {@code event} has been processed.
     *
     * <p>Backed by a single INSERT that relies on a DB-level uniqueness guarantee on
     * {@code eventId} — never {@code exists()} followed by a separate {@code insert()}, which
     * would leave a race window under concurrent delivery of the same message.
     *
     * @return {@code true} if this was the first time {@code event.eventId()} was recorded,
     *         {@code false} if it was already present (a duplicate).
     */
    boolean tryMarkProcessed(ProcessedEvent event);
}

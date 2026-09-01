package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

import java.util.List;

/** Secondary port for persisting {@link OutboxEvent}s and reading back pending batches to relay. */
public interface OutboxRepository {

    /** Upsert: used both to insert a brand-new event and to persist a status transition. */
    void save(OutboxEvent event);

    /** Reads up to {@code batchSize} events currently {@code PENDING}, oldest first. */
    List<OutboxEvent> findPendingBatch(int batchSize);
}

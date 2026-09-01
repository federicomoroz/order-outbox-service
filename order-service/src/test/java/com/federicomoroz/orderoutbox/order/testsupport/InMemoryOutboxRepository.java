package com.federicomoroz.orderoutbox.order.testsupport;

import com.federicomoroz.orderoutbox.order.application.port.out.OutboxQueryPort;
import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxEventId;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Implements both the relay's write port and the read-only {@link OutboxQueryPort}, mirroring
 * the real {@code OutboxEventPersistenceAdapter}. */
public final class InMemoryOutboxRepository implements OutboxRepository, OutboxQueryPort {

    private final Map<OutboxEventId, OutboxEvent> store = new LinkedHashMap<>();

    @Override
    public void save(OutboxEvent event) {
        store.put(event.id(), event);
    }

    /**
     * Mirrors the real adapter's query, including the part that is easy to forget: an event whose
     * backoff window has not elapsed is simply not returned. The "is it due" decision is delegated
     * to the domain rather than reimplemented here, so this fake cannot drift from the SQL by
     * accident — both are driven by the same rule.
     */
    @Override
    public List<OutboxEvent> findDueBatch(int batchSize, Instant now) {
        return store.values().stream()
                .filter(event -> event.isDueAt(now))
                .sorted(Comparator.comparing(OutboxEvent::occurredAt))
                .limit(batchSize)
                .toList();
    }

    @Override
    public List<OutboxEvent> findRecent(int limit) {
        return store.values().stream()
                .sorted(Comparator.comparing(OutboxEvent::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    public int size() {
        return store.size();
    }

    public List<OutboxEvent> all() {
        return List.copyOf(store.values());
    }
}

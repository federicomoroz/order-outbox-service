package com.federicomoroz.orderoutbox.order.testsupport;

import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxEventId;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryOutboxRepository implements OutboxRepository {

    private final Map<OutboxEventId, OutboxEvent> store = new LinkedHashMap<>();

    @Override
    public void save(OutboxEvent event) {
        store.put(event.id(), event);
    }

    @Override
    public List<OutboxEvent> findPendingBatch(int batchSize) {
        return store.values().stream()
                .filter(event -> event.status() == OutboxStatus.PENDING)
                .limit(batchSize)
                .toList();
    }

    public int size() {
        return store.size();
    }

    public List<OutboxEvent> all() {
        return List.copyOf(store.values());
    }
}

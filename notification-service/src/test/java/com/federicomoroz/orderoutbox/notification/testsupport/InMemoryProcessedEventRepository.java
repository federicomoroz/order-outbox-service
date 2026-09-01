package com.federicomoroz.orderoutbox.notification.testsupport;

import com.federicomoroz.orderoutbox.notification.application.port.out.ProcessedEventRepository;
import com.federicomoroz.orderoutbox.notification.domain.ProcessedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Hand-written in-memory fake. The real correctness of "single INSERT guarded by a DB
 * constraint" is proven by {@code ProcessedEventPersistenceAdapterIT} against real Postgres —
 * this fake only needs to model the same true/false contract for pure application-service tests. */
public final class InMemoryProcessedEventRepository implements ProcessedEventRepository {

    private final Map<UUID, ProcessedEvent> store = new LinkedHashMap<>();

    @Override
    public boolean tryMarkProcessed(ProcessedEvent event) {
        if (store.containsKey(event.eventId())) {
            return false;
        }
        store.put(event.eventId(), event);
        return true;
    }

    public int size() {
        return store.size();
    }
}

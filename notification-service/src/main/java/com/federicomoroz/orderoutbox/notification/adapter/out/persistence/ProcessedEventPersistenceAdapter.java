package com.federicomoroz.orderoutbox.notification.adapter.out.persistence;

import com.federicomoroz.orderoutbox.notification.application.port.out.ProcessedEventRepository;
import com.federicomoroz.orderoutbox.notification.domain.ProcessedEvent;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventPersistenceAdapter implements ProcessedEventRepository {

    private static final int ROW_INSERTED = 1;

    private final ProcessedEventJpaRepository jpaRepository;

    ProcessedEventPersistenceAdapter(ProcessedEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean tryMarkProcessed(ProcessedEvent event) {
        int rowsInserted = jpaRepository.tryInsert(event.eventId(), event.processedAt());
        return rowsInserted == ROW_INSERTED;
    }
}

package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxEventPersistenceAdapter implements OutboxRepository {

    private final OutboxEventJpaRepository jpaRepository;

    OutboxEventPersistenceAdapter(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OutboxEvent event) {
        jpaRepository.save(OutboxEventMapper.toEntity(event));
    }

    @Override
    public List<OutboxEvent> findPendingBatch(int batchSize) {
        return jpaRepository
                .findByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING.name(), Pageable.ofSize(batchSize))
                .stream()
                .map(OutboxEventMapper::toDomain)
                .toList();
    }
}

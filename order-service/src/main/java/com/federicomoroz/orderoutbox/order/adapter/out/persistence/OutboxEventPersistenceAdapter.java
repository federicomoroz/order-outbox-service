package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.application.port.out.OutboxQueryPort;
import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One adapter, two segregated ports: {@link OutboxRepository} for the relay (write + pending
 * batches) and {@link OutboxQueryPort} for read-only observation of the whole outbox.
 */
@Component
public class OutboxEventPersistenceAdapter implements OutboxRepository, OutboxQueryPort {

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

    @Override
    public List<OutboxEvent> findRecent(int limit) {
        return jpaRepository.findAllByOrderByOccurredAtDesc(Pageable.ofSize(limit))
                .stream()
                .map(OutboxEventMapper::toDomain)
                .toList();
    }
}

package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.application.port.out.OutboxQueryPort;
import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * One adapter, two segregated ports: {@link OutboxRepository} for the relay (write + the batch of
 * events currently due) and {@link OutboxQueryPort} for read-only observation of the whole outbox.
 */
@Component
public class OutboxEventPersistenceAdapter implements OutboxRepository, OutboxQueryPort {

    /**
     * The domain's answer to "which rows are still the relay's business", flattened to the column
     * values the query binds. Derived from {@link OutboxStatus}, never re-listed by hand here.
     */
    private static final List<String> AWAITING_RELAY_STATUS_NAMES = OutboxStatus.awaitingRelay()
            .stream()
            .map(OutboxStatus::name)
            .sorted()
            .toList();

    private final OutboxEventJpaRepository jpaRepository;

    OutboxEventPersistenceAdapter(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OutboxEvent event) {
        jpaRepository.save(OutboxEventMapper.toEntity(event));
    }

    @Override
    public List<OutboxEvent> findDueBatch(int batchSize, Instant now) {
        return jpaRepository
                .findDue(AWAITING_RELAY_STATUS_NAMES, now, Pageable.ofSize(batchSize))
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

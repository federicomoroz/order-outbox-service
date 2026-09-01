package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Package-private on purpose — only {@link OutboxEventPersistenceAdapter} may see raw JPA entities. */
interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    List<OutboxEventJpaEntity> findByStatusOrderByOccurredAtAsc(String status, Pageable pageable);
}

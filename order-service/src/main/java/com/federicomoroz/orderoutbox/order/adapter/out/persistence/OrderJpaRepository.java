package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Package-private on purpose — only {@link OrderPersistenceAdapter} may see raw JPA entities. */
interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

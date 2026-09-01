package com.federicomoroz.orderoutbox.notification.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Package-private on purpose — only {@link NotificationPersistenceAdapter} may see raw JPA entities. */
interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    List<NotificationJpaEntity> findAllByOrderByCreatedAtDesc();
}

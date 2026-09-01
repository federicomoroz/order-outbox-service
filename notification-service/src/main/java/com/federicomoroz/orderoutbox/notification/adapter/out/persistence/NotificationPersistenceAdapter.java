package com.federicomoroz.orderoutbox.notification.adapter.out.persistence;

import com.federicomoroz.orderoutbox.notification.application.port.out.NotificationRepository;
import com.federicomoroz.orderoutbox.notification.domain.Notification;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationPersistenceAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    NotificationPersistenceAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Notification notification) {
        jpaRepository.save(NotificationMapper.toEntity(notification));
    }

    @Override
    public List<Notification> findAll() {
        return jpaRepository.findAllByOrderByCreatedAtDesc().stream().map(NotificationMapper::toDomain).toList();
    }
}

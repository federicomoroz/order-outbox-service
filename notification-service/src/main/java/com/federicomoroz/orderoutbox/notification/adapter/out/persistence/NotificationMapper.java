package com.federicomoroz.orderoutbox.notification.adapter.out.persistence;

import com.federicomoroz.orderoutbox.notification.domain.Notification;
import com.federicomoroz.orderoutbox.notification.domain.NotificationId;

/** Package-private, stateless translation between {@link Notification} and {@link NotificationJpaEntity}. */
final class NotificationMapper {

    private NotificationMapper() {
    }

    static NotificationJpaEntity toEntity(Notification notification) {
        return new NotificationJpaEntity(
                notification.id().value(),
                notification.orderId(),
                notification.customerId(),
                notification.message(),
                notification.createdAt());
    }

    static Notification toDomain(NotificationJpaEntity entity) {
        return new Notification(
                NotificationId.of(entity.getId()),
                entity.getOrderId(),
                entity.getCustomerId(),
                entity.getMessage(),
                entity.getCreatedAt());
    }
}

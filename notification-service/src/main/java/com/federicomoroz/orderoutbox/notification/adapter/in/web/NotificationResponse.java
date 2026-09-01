package com.federicomoroz.orderoutbox.notification.adapter.in.web;

import com.federicomoroz.orderoutbox.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID orderId, UUID customerId, String message, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id().value(),
                notification.orderId(),
                notification.customerId(),
                notification.message(),
                notification.createdAt());
    }
}

package com.federicomoroz.orderoutbox.notification.domain;

import java.util.Objects;
import java.util.UUID;

public record NotificationId(UUID value) {

    public NotificationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static NotificationId newId() {
        return new NotificationId(UUID.randomUUID());
    }

    public static NotificationId of(UUID value) {
        return new NotificationId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

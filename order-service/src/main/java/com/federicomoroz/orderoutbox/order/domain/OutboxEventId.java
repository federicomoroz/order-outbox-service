package com.federicomoroz.orderoutbox.order.domain;

import java.util.Objects;
import java.util.UUID;

public record OutboxEventId(UUID value) {

    public OutboxEventId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static OutboxEventId newId() {
        return new OutboxEventId(UUID.randomUUID());
    }

    public static OutboxEventId of(UUID value) {
        return new OutboxEventId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

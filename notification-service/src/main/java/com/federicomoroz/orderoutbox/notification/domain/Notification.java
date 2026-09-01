package com.federicomoroz.orderoutbox.notification.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A customer-facing notification produced in response to an {@code OrderCreated} event.
 *
 * <p>{@code orderId}/{@code customerId} are raw {@link UUID}s, not this module's own value
 * objects — they reference identities that belong to {@code order-service}'s bounded context,
 * not to this one. Wrapping them in a local {@code OrderId}/{@code CustomerId} type would wrongly
 * imply this service owns or governs those concepts.
 */
public record Notification(
        NotificationId id,
        UUID orderId,
        UUID customerId,
        String message,
        Instant createdAt
) {

    public Notification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static Notification forOrderCreated(UUID orderId, UUID customerId, String productId, int quantity,
                                                 Clock clock) {
        String message = "Your order for %d x %s has been received.".formatted(quantity, productId);
        return new Notification(NotificationId.newId(), orderId, customerId, message, Instant.now(clock));
    }
}

package com.federicomoroz.orderoutbox.order.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * An order placed by a customer for a single product line. Immutable — an {@code Order} never
 * changes after it is created (contrast with {@link OutboxEvent}, whose mutability is
 * deliberate for the opposite reason).
 */
public record Order(
        OrderId id,
        CustomerId customerId,
        String productId,
        int quantity,
        Money unitPrice,
        OrderStatus status,
        Instant createdAt
) {

    public Order {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (productId.isBlank()) {
            throw new InvalidOrderException("productId must not be blank");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("quantity must be greater than zero, got " + quantity);
        }
    }

    /**
     * Places a new order. The only supported way to create an {@code Order} from scratch —
     * reconstruction from persisted state goes through the persistence adapter's mapper instead,
     * never through this factory.
     */
    public static Order place(CustomerId customerId, String productId, int quantity, Money unitPrice, Clock clock) {
        return new Order(OrderId.newId(), customerId, productId, quantity, unitPrice, OrderStatus.CREATED,
                Instant.now(clock));
    }
}

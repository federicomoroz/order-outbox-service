package com.federicomoroz.orderoutbox.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The domain fact serialized into the outbox and, eventually, onto the {@code order.created.v1}
 * Kafka topic. Uses plain wire-friendly types (raw {@link UUID}, {@link BigDecimal}) rather than
 * the {@code Order} aggregate's own value objects ({@code OrderId}, {@code Money}, ...) on
 * purpose: this record IS the external contract, and the external contract should not leak or
 * depend on internal aggregate shape.
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        String productId,
        int quantity,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        Instant occurredAt
) {
}

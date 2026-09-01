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
 *
 * <p>{@code eventId} is the outbox event's own identity (the row's primary key), carried
 * separately from {@code orderId} on purpose: it is the stable key a downstream idempotent
 * consumer deduplicates on. Today one {@code Order} produces exactly one event, so {@code orderId}
 * would work too — but keying off the envelope's own id, not a business key, is what stays
 * correct if that 1:1 assumption ever stops holding.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        String productId,
        int quantity,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        Instant occurredAt
) {
}

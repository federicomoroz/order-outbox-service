package com.federicomoroz.orderoutbox.notification.adapter.in.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * This module's own copy of the {@code order-service} wire contract for
 * {@code order.created.v1}. Deliberately not shared via a common JAR between the two services —
 * see README "Decisiones puntuales" for why. Field-for-field mirrored fixture tests in both
 * modules guard against the two copies drifting apart.
 */
record OrderCreatedEventPayload(
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

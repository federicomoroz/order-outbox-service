package com.federicomoroz.orderoutbox.order.adapter.in.web;

import com.federicomoroz.orderoutbox.order.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String productId,
        int quantity,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        String status,
        Instant createdAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id().value(),
                order.customerId().value(),
                order.productId(),
                order.quantity(),
                order.unitPrice().amount(),
                order.unitPrice().currency().getCurrencyCode(),
                order.status().name(),
                order.createdAt());
    }
}

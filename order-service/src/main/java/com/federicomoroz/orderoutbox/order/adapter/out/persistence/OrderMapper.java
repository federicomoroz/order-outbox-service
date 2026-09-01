package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.domain.CustomerId;
import com.federicomoroz.orderoutbox.order.domain.Money;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;
import com.federicomoroz.orderoutbox.order.domain.OrderStatus;

import java.util.Currency;

/** Package-private, stateless. The only place that knows how to translate between {@link Order}
 * and {@link OrderJpaEntity}. */
final class OrderMapper {

    private OrderMapper() {
    }

    static OrderJpaEntity toEntity(Order order) {
        return new OrderJpaEntity(
                order.id().value(),
                order.customerId().value(),
                order.productId(),
                order.quantity(),
                order.unitPrice().amount(),
                order.unitPrice().currency().getCurrencyCode(),
                order.status().name(),
                order.createdAt());
    }

    static Order toDomain(OrderJpaEntity entity) {
        return new Order(
                OrderId.of(entity.getId()),
                CustomerId.of(entity.getCustomerId()),
                entity.getProductId(),
                entity.getQuantity(),
                new Money(entity.getUnitPriceAmount(), Currency.getInstance(entity.getUnitPriceCurrency())),
                OrderStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt());
    }
}

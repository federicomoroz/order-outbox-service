package com.federicomoroz.orderoutbox.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void placesAnOrderWithGeneratedIdAndCreatedStatus() {
        CustomerId customerId = CustomerId.of(UUID.randomUUID());
        Money unitPrice = Money.of(new BigDecimal("10.00"), "USD");

        Order order = Order.place(customerId, "sku-1", 3, unitPrice, FIXED_CLOCK);

        assertThat(order.id()).isNotNull();
        assertThat(order.customerId()).isEqualTo(customerId);
        assertThat(order.productId()).isEqualTo("sku-1");
        assertThat(order.quantity()).isEqualTo(3);
        assertThat(order.unitPrice()).isEqualTo(unitPrice);
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> Order.place(
                CustomerId.of(UUID.randomUUID()), "sku-1", 0,
                Money.of(BigDecimal.TEN, "USD"), FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> Order.place(
                CustomerId.of(UUID.randomUUID()), "sku-1", -1,
                Money.of(BigDecimal.TEN, "USD"), FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsBlankProductId() {
        assertThatThrownBy(() -> Order.place(
                CustomerId.of(UUID.randomUUID()), "  ", 1,
                Money.of(BigDecimal.TEN, "USD"), FIXED_CLOCK))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void twoOrdersPlacedSeparatelyHaveDifferentIds() {
        Order first = Order.place(CustomerId.of(UUID.randomUUID()), "sku-1", 1,
                Money.of(BigDecimal.ONE, "USD"), FIXED_CLOCK);
        Order second = Order.place(CustomerId.of(UUID.randomUUID()), "sku-1", 1,
                Money.of(BigDecimal.ONE, "USD"), FIXED_CLOCK);

        assertThat(first.id()).isNotEqualTo(second.id());
    }
}

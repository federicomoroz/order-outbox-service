package com.federicomoroz.orderoutbox.order.application.service;

import com.federicomoroz.orderoutbox.order.domain.CustomerId;
import com.federicomoroz.orderoutbox.order.domain.Money;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetOrderServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void returnsThePreviouslySavedOrder() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), "sku-1", 1,
                Money.of(BigDecimal.TEN, "USD"), FIXED_CLOCK);
        repository.save(order);
        GetOrderService service = new GetOrderService(repository);

        assertThat(service.getOrder(order.id())).contains(order);
    }

    @Test
    void returnsEmptyWhenOrderDoesNotExist() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        GetOrderService service = new GetOrderService(repository);

        assertThat(service.getOrder(OrderId.newId())).isEmpty();
    }
}

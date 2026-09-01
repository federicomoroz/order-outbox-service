package com.federicomoroz.orderoutbox.order.application.service;

import com.federicomoroz.orderoutbox.order.application.port.in.CreateOrderCommand;
import com.federicomoroz.orderoutbox.order.domain.InvalidOrderException;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;
import com.federicomoroz.orderoutbox.order.testsupport.FakeEventSerializer;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOrderRepository;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOutboxRepository;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateOrderServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private InMemoryOrderRepository orderRepository;
    private InMemoryOutboxRepository outboxRepository;
    private CreateOrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        outboxRepository = new InMemoryOutboxRepository();
        service = new CreateOrderService(orderRepository, outboxRepository, new FakeEventSerializer(),
                new InMemoryTransactionRunner(), FIXED_CLOCK);
    }

    @Test
    void createsOrderAndPersistsItThroughTheTransactionRunner() {
        CreateOrderCommand command = new CreateOrderCommand(
                UUID.randomUUID(), "sku-1", 2, new BigDecimal("9.99"), "USD");

        Order created = service.createOrder(command);

        assertThat(orderRepository.findById(created.id())).contains(created);
        assertThat(orderRepository.size()).isEqualTo(1);
    }

    @Test
    void writesExactlyOnePendingOutboxEventInTheSameCallAsTheOrder() {
        CreateOrderCommand command = new CreateOrderCommand(
                UUID.randomUUID(), "sku-1", 2, new BigDecimal("9.99"), "USD");

        Order created = service.createOrder(command);

        assertThat(outboxRepository.size()).isEqualTo(1);
        OutboxEvent event = outboxRepository.all().get(0);
        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.aggregateType()).isEqualTo("Order");
        assertThat(event.aggregateId()).isEqualTo(created.id().toString());
        assertThat(event.eventType()).isEqualTo("OrderCreated");
        assertThat(event.payload()).contains(created.id().value().toString());
    }

    @Test
    void propagatesInvalidOrderExceptionAndWritesNothing() {
        CreateOrderCommand invalidCommand = new CreateOrderCommand(
                UUID.randomUUID(), "sku-1", 0, new BigDecimal("9.99"), "USD");

        assertThatThrownBy(() -> service.createOrder(invalidCommand))
                .isInstanceOf(InvalidOrderException.class);

        assertThat(orderRepository.size()).isZero();
        assertThat(outboxRepository.size()).isZero();
    }
}

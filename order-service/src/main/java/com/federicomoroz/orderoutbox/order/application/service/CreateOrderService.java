package com.federicomoroz.orderoutbox.order.application.service;

import com.federicomoroz.orderoutbox.order.application.port.in.CreateOrderCommand;
import com.federicomoroz.orderoutbox.order.application.port.in.CreateOrderUseCase;
import com.federicomoroz.orderoutbox.order.application.port.out.EventSerializer;
import com.federicomoroz.orderoutbox.order.application.port.out.OrderRepository;
import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.application.port.out.TransactionRunner;
import com.federicomoroz.orderoutbox.order.domain.CustomerId;
import com.federicomoroz.orderoutbox.order.domain.Money;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.event.OrderCreatedEvent;

import java.time.Clock;

/**
 * A plain Java class — no Spring annotations. Everything it needs (repositories, the
 * transaction boundary, the clock) arrives through the constructor, wired by
 * {@code OrderServiceBeanConfiguration}.
 */
public final class CreateOrderService implements CreateOrderUseCase {

    static final String AGGREGATE_TYPE_ORDER = "Order";
    static final String EVENT_TYPE_ORDER_CREATED = "OrderCreated";

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final EventSerializer eventSerializer;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public CreateOrderService(OrderRepository orderRepository, OutboxRepository outboxRepository,
                               EventSerializer eventSerializer, TransactionRunner transactionRunner, Clock clock) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.eventSerializer = eventSerializer;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        Order order = Order.place(
                CustomerId.of(command.customerId()),
                command.productId(),
                command.quantity(),
                Money.of(command.unitPriceAmount(), command.unitPriceCurrency()),
                clock);

        OutboxEvent outboxEvent = toOutboxEvent(order);

        transactionRunner.run(() -> {
            orderRepository.save(order);
            outboxRepository.save(outboxEvent);
        });

        return order;
    }

    private OutboxEvent toOutboxEvent(Order order) {
        OrderCreatedEvent domainEvent = new OrderCreatedEvent(
                order.id().value(),
                order.customerId().value(),
                order.productId(),
                order.quantity(),
                order.unitPrice().amount(),
                order.unitPrice().currency().getCurrencyCode(),
                order.createdAt());

        String payload = eventSerializer.serialize(domainEvent);

        return OutboxEvent.record(AGGREGATE_TYPE_ORDER, order.id().toString(), EVENT_TYPE_ORDER_CREATED, payload,
                order.createdAt());
    }
}

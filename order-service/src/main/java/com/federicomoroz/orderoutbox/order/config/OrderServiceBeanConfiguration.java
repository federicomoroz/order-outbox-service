package com.federicomoroz.orderoutbox.order.config;

import com.federicomoroz.orderoutbox.order.application.port.in.CreateOrderUseCase;
import com.federicomoroz.orderoutbox.order.application.port.in.GetOrderUseCase;
import com.federicomoroz.orderoutbox.order.application.port.in.RelayOutboxEventsUseCase;
import com.federicomoroz.orderoutbox.order.application.port.out.EventPublisherPort;
import com.federicomoroz.orderoutbox.order.application.port.out.EventSerializer;
import com.federicomoroz.orderoutbox.order.application.port.out.OrderRepository;
import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.application.port.out.TransactionRunner;
import com.federicomoroz.orderoutbox.order.application.service.CreateOrderService;
import com.federicomoroz.orderoutbox.order.application.service.GetOrderService;
import com.federicomoroz.orderoutbox.order.application.service.OutboxRelayService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root: the one place in this module where the framework-free
 * {@code application/service} classes are instantiated and wired to their Spring-managed
 * adapters. Everything upstream of this class (domain, application) has zero knowledge that
 * Spring exists; everything downstream (the adapters passed in as constructor arguments) is a
 * regular {@code @Component}.
 */
@Configuration
public class OrderServiceBeanConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CreateOrderUseCase createOrderUseCase(OrderRepository orderRepository, OutboxRepository outboxRepository,
                                                   EventSerializer eventSerializer, TransactionRunner transactionRunner,
                                                   Clock clock) {
        return new CreateOrderService(orderRepository, outboxRepository, eventSerializer, transactionRunner, clock);
    }

    @Bean
    public GetOrderUseCase getOrderUseCase(OrderRepository orderRepository) {
        return new GetOrderService(orderRepository);
    }

    @Bean
    public RelayOutboxEventsUseCase relayOutboxEventsUseCase(OutboxRepository outboxRepository,
                                                               EventPublisherPort eventPublisherPort,
                                                               TransactionRunner transactionRunner, Clock clock) {
        return new OutboxRelayService(outboxRepository, eventPublisherPort, transactionRunner, clock);
    }
}

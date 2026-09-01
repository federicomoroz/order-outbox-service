package com.federicomoroz.orderoutbox.order.application.service;

import com.federicomoroz.orderoutbox.order.application.port.in.GetOrderUseCase;
import com.federicomoroz.orderoutbox.order.application.port.out.OrderRepository;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;

import java.util.Optional;

/** Plain Java class — a thin, read-only pass-through to {@link OrderRepository}. */
public final class GetOrderService implements GetOrderUseCase {

    private final OrderRepository orderRepository;

    public GetOrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Optional<Order> getOrder(OrderId id) {
        return orderRepository.findById(id);
    }
}

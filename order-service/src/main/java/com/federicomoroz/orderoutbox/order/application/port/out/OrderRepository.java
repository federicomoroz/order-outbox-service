package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;

import java.util.Optional;

/** Secondary port for persisting and retrieving {@link Order} aggregates. */
public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId id);
}

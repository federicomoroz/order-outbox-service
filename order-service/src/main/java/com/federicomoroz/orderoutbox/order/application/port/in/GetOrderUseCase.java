package com.federicomoroz.orderoutbox.order.application.port.in;

import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;

import java.util.Optional;

/** Primary port for retrieving a previously placed order by id. */
public interface GetOrderUseCase {

    Optional<Order> getOrder(OrderId id);
}

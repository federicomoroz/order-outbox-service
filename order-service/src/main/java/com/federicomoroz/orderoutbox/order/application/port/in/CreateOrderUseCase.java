package com.federicomoroz.orderoutbox.order.application.port.in;

import com.federicomoroz.orderoutbox.order.domain.Order;

/**
 * Primary port for placing an order.
 *
 * <p>Left explicit with a single implementation ({@code CreateOrderService}) so the hexagon's
 * inbound side stays visible end-to-end: in a real project with only one use case implementation
 * you would often inline this call in the controller instead. Same criterion applied in
 * {@code shipping-quote}'s {@code ShippingQuotePort}.
 */
public interface CreateOrderUseCase {

    Order createOrder(CreateOrderCommand command);
}

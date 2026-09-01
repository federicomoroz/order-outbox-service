package com.federicomoroz.orderoutbox.order.testsupport;

import com.federicomoroz.orderoutbox.order.application.port.out.OrderRepository;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Hand-written in-memory fake — no Mockito. Mirrors the {@code StaticPool} SQLite-in-memory
 * philosophy used elsewhere in the portfolio: real-but-lightweight, not mocked-away. */
public final class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> store = new LinkedHashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return Optional.ofNullable(store.get(id));
    }

    public int size() {
        return store.size();
    }
}

package com.federicomoroz.orderoutbox.order.testsupport;

import com.federicomoroz.orderoutbox.order.application.port.out.OrderQueryPort;
import com.federicomoroz.orderoutbox.order.application.port.out.OrderRepository;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written in-memory fake — no Mockito. Mirrors the {@code StaticPool} SQLite-in-memory
 * philosophy used elsewhere in the portfolio: real-but-lightweight, not mocked-away.
 *
 * <p>Implements both the write port and the read-only {@link OrderQueryPort}, exactly like the
 * real {@code OrderPersistenceAdapter} does — the ports stay segregated for callers, the
 * implementation is shared. */
public final class InMemoryOrderRepository implements OrderRepository, OrderQueryPort {

    private final Map<OrderId, Order> store = new LinkedHashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findRecent(int limit) {
        return store.values().stream()
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    public int size() {
        return store.size();
    }
}

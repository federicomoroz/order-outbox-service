package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.Order;

import java.util.List;

/**
 * Read-only secondary port for listing orders, deliberately kept apart from
 * {@link OrderRepository}.
 *
 * <p>Interface Segregation taken literally: {@code OrderRepository} exists to serve the
 * create-order use case ({@code save} + {@code findById} of a single aggregate). The dashboard
 * needs something else entirely — a bounded, newest-first listing — and bolting that onto
 * {@code OrderRepository} would force every implementation of it (including the hand-written
 * in-memory fakes used by {@code CreateOrderServiceTest}) to grow a method that use case never
 * calls. Two narrow ports, one adapter implementing both, no coupling between unrelated
 * consumers.
 */
public interface OrderQueryPort {

    /** Most recently created orders first, at most {@code limit} of them. */
    List<Order> findRecent(int limit);
}

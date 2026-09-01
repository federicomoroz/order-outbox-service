package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.application.port.out.OrderQueryPort;
import com.federicomoroz.orderoutbox.order.application.port.out.OrderRepository;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * One adapter, two segregated ports: {@link OrderRepository} for the write path and
 * {@link OrderQueryPort} for read-only listings. Sharing the implementation is fine — the
 * point of the split is that <em>callers</em> depend only on the slice they use.
 */
@Component
public class OrderPersistenceAdapter implements OrderRepository, OrderQueryPort {

    private final OrderJpaRepository jpaRepository;

    OrderPersistenceAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Order order) {
        jpaRepository.save(OrderMapper.toEntity(order));
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpaRepository.findById(id.value()).map(OrderMapper::toDomain);
    }

    @Override
    public List<Order> findRecent(int limit) {
        return jpaRepository.findAllByOrderByCreatedAtDesc(Pageable.ofSize(limit))
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }
}

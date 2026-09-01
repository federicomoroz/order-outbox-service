package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.domain.CustomerId;
import com.federicomoroz.orderoutbox.order.domain.Money;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The flagship test of the whole repo: proves the Transactional Outbox pattern's core promise —
 * {@code Order} and {@code OutboxEvent} really do land in exactly one atomic DB transaction —
 * against a real Postgres via Testcontainers, not a fake.
 *
 * <p>Scoped to just the persistence package (via the nested {@code PersistenceTestConfig}) so
 * this test needs neither Kafka nor the web layer — only JPA, Flyway, and a real DB.
 */
@SpringBootTest(classes = OrderPersistenceAdapterIT.PersistenceTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OrderPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private OrderPersistenceAdapter orderPersistenceAdapter;

    @Autowired
    private OutboxEventPersistenceAdapter outboxEventPersistenceAdapter;

    @Autowired
    private SpringTransactionRunner transactionRunner;

    @Test
    void savedOrderCanBeReadBackFromRealPostgres() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), "sku-1", 2,
                Money.of(new BigDecimal("9.99"), "USD"), FIXED_CLOCK);

        transactionRunner.run(() -> orderPersistenceAdapter.save(order));

        assertThat(orderPersistenceAdapter.findById(order.id())).contains(order);
    }

    @Test
    void savedOutboxEventCanBeReadBackInAPendingBatch() {
        OutboxEvent event = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.now(FIXED_CLOCK));

        transactionRunner.run(() -> outboxEventPersistenceAdapter.save(event));

        assertThat(outboxEventPersistenceAdapter.findPendingBatch(50))
                .extracting(OutboxEvent::id)
                .contains(event.id());
    }

    @Test
    void aFailureMidTransactionRollsBackBothTheOrderAndTheOutboxEvent_realAtomicity() {
        Order order = Order.place(CustomerId.of(UUID.randomUUID()), "sku-atomic", 1,
                Money.of(BigDecimal.TEN, "USD"), FIXED_CLOCK);
        OutboxEvent outboxEvent = OutboxEvent.record("Order", order.id().toString(), "OrderCreated", "{}",
                Instant.now(FIXED_CLOCK));

        assertThatThrownBy(() -> transactionRunner.run(() -> {
            orderPersistenceAdapter.save(order);
            outboxEventPersistenceAdapter.save(outboxEvent);
            throw new RuntimeException("simulated failure after both writes, before commit");
        })).isInstanceOf(RuntimeException.class);

        // Real proof of atomicity: read back through a brand-new call (new transaction/read),
        // against the real Postgres container — not an in-memory fake that can't lie about this.
        assertThat(orderPersistenceAdapter.findById(order.id())).isEmpty();
        assertThat(outboxEventPersistenceAdapter.findPendingBatch(50))
                .extracting(OutboxEvent::id)
                .doesNotContain(outboxEvent.id());
    }

    @SpringBootApplication
    static class PersistenceTestConfig {
    }
}

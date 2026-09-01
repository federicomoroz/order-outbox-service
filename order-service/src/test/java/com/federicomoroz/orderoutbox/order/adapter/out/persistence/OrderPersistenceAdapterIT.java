package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.domain.CustomerId;
import com.federicomoroz.orderoutbox.order.domain.Money;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
    void savedOutboxEventCanBeReadBackInADueBatch() {
        OutboxEvent event = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.now(FIXED_CLOCK));

        transactionRunner.run(() -> outboxEventPersistenceAdapter.save(event));

        assertThat(outboxEventPersistenceAdapter.findDueBatch(50, Instant.now(FIXED_CLOCK)))
                .extracting(OutboxEvent::id)
                .contains(event.id());
    }

    /**
     * The backoff has to hold against real SQL, not just against the in-memory fake: the
     * {@code next_attempt_at IS NULL OR next_attempt_at <= now} half of the relay's query is
     * three-valued logic over a nullable timestamp, which is exactly the kind of thing a fake
     * gets right by accident and Postgres does not.
     */
    @Test
    void aBackedOffEventIsInvisibleToTheRelayUntilItsWindowElapses_realPostgres() {
        Instant failedAt = Instant.parse("2025-08-01T10:00:00Z");
        OutboxEvent event = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.parse("2025-08-01T09:59:00Z"));
        event.recordFailedAttempt(failedAt);
        Instant nextAttemptAt = event.nextAttemptAt();

        transactionRunner.run(() -> outboxEventPersistenceAdapter.save(event));

        assertThat(outboxEventPersistenceAdapter.findDueBatch(50, nextAttemptAt.minusMillis(1)))
                .extracting(OutboxEvent::id)
                .doesNotContain(event.id());
        assertThat(outboxEventPersistenceAdapter.findDueBatch(50, nextAttemptAt))
                .extracting(OutboxEvent::id)
                .contains(event.id());
        assertThat(outboxEventPersistenceAdapter.findDueBatch(50, nextAttemptAt.plus(Duration.ofHours(1))))
                .extracting(OutboxEvent::id)
                .contains(event.id());
    }

    /**
     * The behavioural change this whole migration exists for: {@code FAILED} is no longer
     * terminal. The relay's query must keep handing those rows back once they are due, otherwise
     * a degraded event silently stops being retried and needs a human after all.
     */
    @Test
    void aFailedEventIsStillReturnedToTheRelayOnceItIsDue_failedIsNotTerminal() {
        Instant failedAt = Instant.parse("2025-08-02T10:00:00Z");
        OutboxEvent event = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.parse("2025-08-02T09:59:00Z"));
        for (int attempt = 0; attempt < OutboxEvent.MAX_PUBLISH_ATTEMPTS; attempt++) {
            event.recordFailedAttempt(failedAt);
        }
        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);

        transactionRunner.run(() -> outboxEventPersistenceAdapter.save(event));

        List<OutboxEvent> due = outboxEventPersistenceAdapter.findDueBatch(50, event.nextAttemptAt());
        assertThat(due).extracting(OutboxEvent::id).contains(event.id());
        assertThat(due)
                .filteredOn(candidate -> candidate.id().equals(event.id()))
                .singleElement()
                .satisfies(reloaded -> {
                    assertThat(reloaded.status()).isEqualTo(OutboxStatus.FAILED);
                    assertThat(reloaded.publishAttempts()).isEqualTo(OutboxEvent.MAX_PUBLISH_ATTEMPTS);
                    assertThat(reloaded.nextAttemptAt()).isEqualTo(event.nextAttemptAt());
                });
    }

    @Test
    void aPublishedEventIsNeverReturnedToTheRelay_howeverLongAgoItWasDue() {
        OutboxEvent event = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.parse("2025-08-03T09:59:00Z"));
        event.recordFailedAttempt(Instant.parse("2025-08-03T10:00:00Z"));
        event.markPublished(Instant.parse("2025-08-03T10:00:30Z"));

        transactionRunner.run(() -> outboxEventPersistenceAdapter.save(event));

        assertThat(outboxEventPersistenceAdapter.findDueBatch(50, Instant.parse("2025-12-01T00:00:00Z")))
                .extracting(OutboxEvent::id)
                .doesNotContain(event.id());
    }

    @Test
    void dueEventsComeBackOldestFirst_realOrderByAgainstPostgres() {
        Instant now = Instant.parse("2025-08-04T12:00:00Z");
        OutboxEvent older = OutboxEvent.record("Order", "order-due-older", "OrderCreated", "{}",
                Instant.parse("2025-08-04T10:00:00Z"));
        OutboxEvent newer = OutboxEvent.record("Order", "order-due-newer", "OrderCreated", "{}",
                Instant.parse("2025-08-04T11:00:00Z"));

        transactionRunner.run(() -> {
            outboxEventPersistenceAdapter.save(newer);
            outboxEventPersistenceAdapter.save(older);
        });

        List<OutboxEvent> due = outboxEventPersistenceAdapter.findDueBatch(50, now);
        assertThat(due).extracting(OutboxEvent::aggregateId)
                .filteredOn(aggregateId -> aggregateId.startsWith("order-due-"))
                .containsExactly("order-due-older", "order-due-newer");
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
        assertThat(outboxEventPersistenceAdapter.findDueBatch(50, Instant.now(FIXED_CLOCK)))
                .extracting(OutboxEvent::id)
                .doesNotContain(outboxEvent.id());
    }

    @Test
    void recentOrdersComeBackNewestFirst_realOrderByAgainstPostgres() {
        Order older = orderPlacedAt("sku-recent-older", "2026-06-01T10:00:00Z");
        Order newer = orderPlacedAt("sku-recent-newer", "2026-06-01T10:00:05Z");

        transactionRunner.run(() -> {
            orderPersistenceAdapter.save(older);
            orderPersistenceAdapter.save(newer);
        });

        // Both timestamps are deliberately later than every other order this class writes, so
        // these two are always the newest regardless of the order JUnit runs the methods in.
        assertThat(orderPersistenceAdapter.findRecent(2))
                .containsExactly(newer, older);
    }

    @Test
    void recentOrdersRespectTheRequestedLimit() {
        transactionRunner.run(() -> {
            orderPersistenceAdapter.save(orderPlacedAt("sku-limit-a", "2026-07-01T10:00:00Z"));
            orderPersistenceAdapter.save(orderPlacedAt("sku-limit-b", "2026-07-01T10:00:01Z"));
            orderPersistenceAdapter.save(orderPlacedAt("sku-limit-c", "2026-07-01T10:00:02Z"));
        });

        assertThat(orderPersistenceAdapter.findRecent(2)).hasSize(2);
    }

    @Test
    void recentOutboxEventsComeBackNewestFirst_withLifecycleStateIntact() {
        OutboxEvent published = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.parse("2026-06-01T10:00:00Z"));
        published.markPublished(Instant.parse("2026-06-01T10:00:03Z"));
        OutboxEvent pending = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.parse("2026-06-01T10:00:05Z"));

        transactionRunner.run(() -> {
            outboxEventPersistenceAdapter.save(published);
            outboxEventPersistenceAdapter.save(pending);
        });

        List<OutboxEvent> recent = outboxEventPersistenceAdapter.findRecent(2);

        // Unlike findDueBatch, the query port has to surface already-PUBLISHED rows too —
        // that is the whole point of the dashboard's outbox column.
        assertThat(recent).extracting(OutboxEvent::id).containsExactly(pending.id(), published.id());
        assertThat(recent.get(0).status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(recent.get(0).publishedAt()).isNull();
        assertThat(recent.get(1).status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(recent.get(1).publishedAt()).isEqualTo(Instant.parse("2026-06-01T10:00:03Z"));
        assertThat(recent.get(1).publishAttempts()).isZero();
        assertThat(recent.get(1).nextAttemptAt()).isNull();
    }

    private static Order orderPlacedAt(String productId, String createdAt) {
        return Order.place(CustomerId.of(UUID.randomUUID()), productId, 1,
                Money.of(new BigDecimal("9.99"), "USD"),
                Clock.fixed(Instant.parse(createdAt), ZoneOffset.UTC));
    }

    @SpringBootApplication
    static class PersistenceTestConfig {
    }
}

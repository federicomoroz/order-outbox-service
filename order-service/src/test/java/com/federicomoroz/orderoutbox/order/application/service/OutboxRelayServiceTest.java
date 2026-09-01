package com.federicomoroz.orderoutbox.order.application.service;

import com.federicomoroz.orderoutbox.order.application.port.in.RelayOutboxEventsUseCase.RelayOutcome;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;
import com.federicomoroz.orderoutbox.order.testsupport.FakeEventPublisher;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOutboxRepository;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final Instant OCCURRED_AT = Instant.parse("2025-12-31T23:59:00Z");

    private InMemoryOutboxRepository outboxRepository;
    private FakeEventPublisher eventPublisher;
    private OutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        outboxRepository = new InMemoryOutboxRepository();
        eventPublisher = new FakeEventPublisher();
        relayService = new OutboxRelayService(outboxRepository, eventPublisher, new InMemoryTransactionRunner(),
                FIXED_CLOCK);
    }

    @Test
    void publishesEveryPendingEventAndMarksThemPublished() {
        outboxRepository.save(OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT));
        outboxRepository.save(OutboxEvent.record("Order", "order-2", "OrderCreated", "{}", OCCURRED_AT));

        RelayOutcome outcome = relayService.relayPendingEvents();

        assertThat(outcome.publishedCount()).isEqualTo(2);
        assertThat(outcome.failedCount()).isZero();
        assertThat(outcome.retriedCount()).isZero();
        assertThat(outboxRepository.all()).allSatisfy(event -> {
            assertThat(event.status()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(event.publishedAt()).isNotNull();
        });
        assertThat(eventPublisher.published()).hasSize(2);
    }

    @Test
    void oneFailingEventDoesNotStopOthersFromBeingPublished_perEventIsolation() {
        outboxRepository.save(OutboxEvent.record("Order", "order-ok-1", "OrderCreated", "{}", OCCURRED_AT));
        outboxRepository.save(OutboxEvent.record("Order", "order-bad", "OrderCreated", "{}", OCCURRED_AT));
        outboxRepository.save(OutboxEvent.record("Order", "order-ok-2", "OrderCreated", "{}", OCCURRED_AT));
        eventPublisher.failFor("order-bad");

        RelayOutcome outcome = relayService.relayPendingEvents();

        assertThat(outcome.publishedCount()).isEqualTo(2);
        assertThat(outcome.retriedCount()).isEqualTo(1);
        assertThat(outcome.failedCount()).isZero();

        assertThat(statusOf("order-ok-1")).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(statusOf("order-ok-2")).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void failedEventStaysPendingAndIsRetriedOnTheNextRelayRun() {
        outboxRepository.save(OutboxEvent.record("Order", "order-bad", "OrderCreated", "{}", OCCURRED_AT));
        eventPublisher.failFor("order-bad");

        relayService.relayPendingEvents();
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PENDING);

        eventPublisher.stopFailing("order-bad");
        RelayOutcome secondRun = relayService.relayPendingEvents();

        assertThat(secondRun.publishedCount()).isEqualTo(1);
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void transitionsToFailedAfterMaxPublishAttemptsAndStopsBeingRetried() {
        outboxRepository.save(OutboxEvent.record("Order", "order-bad", "OrderCreated", "{}", OCCURRED_AT));
        eventPublisher.failFor("order-bad");

        RelayOutcome lastOutcome = null;
        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS; i++) {
            lastOutcome = relayService.relayPendingEvents();
        }

        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.FAILED);
        assertThat(lastOutcome.failedCount()).isEqualTo(1);

        // A FAILED event is no longer picked up by findPendingBatch, so further runs are no-ops for it.
        RelayOutcome afterFailed = relayService.relayPendingEvents();
        assertThat(afterFailed.publishedCount()).isZero();
        assertThat(afterFailed.failedCount()).isZero();
        assertThat(afterFailed.retriedCount()).isZero();
    }

    @Test
    void respectsTheRelayBatchSizeConstant() {
        for (int i = 0; i < OutboxRelayService.RELAY_BATCH_SIZE + 10; i++) {
            outboxRepository.save(OutboxEvent.record("Order", "order-" + i, "OrderCreated", "{}", OCCURRED_AT));
        }

        RelayOutcome outcome = relayService.relayPendingEvents();

        assertThat(outcome.publishedCount()).isEqualTo(OutboxRelayService.RELAY_BATCH_SIZE);
    }

    private OutboxStatus statusOf(String aggregateId) {
        return outboxRepository.all().stream()
                .filter(event -> event.aggregateId().equals(aggregateId))
                .findFirst()
                .orElseThrow()
                .status();
    }
}

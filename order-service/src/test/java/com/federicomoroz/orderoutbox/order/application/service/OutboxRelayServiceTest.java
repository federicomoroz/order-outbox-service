package com.federicomoroz.orderoutbox.order.application.service;

import com.federicomoroz.orderoutbox.order.application.port.in.RelayOutboxEventsUseCase.RelayOutcome;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;
import com.federicomoroz.orderoutbox.order.domain.RetryBackoffPolicy;
import com.federicomoroz.orderoutbox.order.testsupport.AdjustableClock;
import com.federicomoroz.orderoutbox.order.testsupport.FakeEventPublisher;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOutboxRepository;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2025-12-31T23:59:00Z");

    private AdjustableClock clock;
    private InMemoryOutboxRepository outboxRepository;
    private FakeEventPublisher eventPublisher;
    private OutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock(START);
        outboxRepository = new InMemoryOutboxRepository();
        eventPublisher = new FakeEventPublisher();
        relayService = new OutboxRelayService(outboxRepository, eventPublisher, new InMemoryTransactionRunner(),
                clock);
    }

    @Test
    void publishesEveryDueEventAndMarksThemPublished() {
        outboxRepository.save(OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT));
        outboxRepository.save(OutboxEvent.record("Order", "order-2", "OrderCreated", "{}", OCCURRED_AT));

        RelayOutcome outcome = relayService.relayDueEvents();

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

        RelayOutcome outcome = relayService.relayDueEvents();

        assertThat(outcome.publishedCount()).isEqualTo(2);
        assertThat(outcome.retriedCount()).isEqualTo(1);
        assertThat(outcome.failedCount()).isZero();

        assertThat(statusOf("order-ok-1")).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(statusOf("order-ok-2")).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PENDING);
    }

    /**
     * The behaviour the backoff exists for. Without it the relay re-attempts a broken event on
     * every single poll, hammering a broker that is already down; here the second run inside the
     * backoff window has literally nothing to do.
     */
    @Test
    void skipsAnEventWhoseBackoffWindowHasNotElapsedYet() {
        outboxRepository.save(OutboxEvent.record("Order", "order-bad", "OrderCreated", "{}", OCCURRED_AT));
        eventPublisher.failFor("order-bad");

        relayService.relayDueEvents();
        assertThat(attemptsOf("order-bad")).isEqualTo(1);
        Instant scheduledRetry = eventOf("order-bad").nextAttemptAt();
        assertThat(scheduledRetry).isAfter(START);

        // The scheduler keeps firing; the relay must decline the work, not repeat it.
        eventPublisher.stopFailing("order-bad");
        clock.advanceBy(Duration.ofMillis(1));
        RelayOutcome tooSoon = relayService.relayDueEvents();

        assertThat(tooSoon.publishedCount()).isZero();
        assertThat(tooSoon.retriedCount()).isZero();
        assertThat(tooSoon.failedCount()).isZero();
        assertThat(attemptsOf("order-bad")).isEqualTo(1);
        assertThat(eventPublisher.published()).isEmpty();
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PENDING);

        // Once the window elapses, the very same event is picked up again.
        clock.advanceBy(Duration.between(clock.instant(), scheduledRetry));
        assertThat(relayService.relayDueEvents().publishedCount()).isEqualTo(1);
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void failedEventStaysPendingAndIsRetriedOnceItsBackoffElapses() {
        outboxRepository.save(OutboxEvent.record("Order", "order-bad", "OrderCreated", "{}", OCCURRED_AT));
        eventPublisher.failFor("order-bad");

        relayService.relayDueEvents();
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PENDING);

        eventPublisher.stopFailing("order-bad");
        clock.advanceBy(RetryBackoffPolicy.delayAfterAttempt(1));
        RelayOutcome secondRun = relayService.relayDueEvents();

        assertThat(secondRun.publishedCount()).isEqualTo(1);
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void attemptsSpreadOutAsFailuresAccumulate() {
        outboxRepository.save(OutboxEvent.record("Order", "order-bad", "OrderCreated", "{}", OCCURRED_AT));
        eventPublisher.failFor("order-bad");

        relayService.relayDueEvents();
        Duration firstGap = Duration.between(clock.instant(), eventOf("order-bad").nextAttemptAt());

        clock.advanceBy(firstGap);
        relayService.relayDueEvents();
        Duration secondGap = Duration.between(clock.instant(), eventOf("order-bad").nextAttemptAt());

        assertThat(attemptsOf("order-bad")).isEqualTo(2);
        assertThat(secondGap).isGreaterThan(firstGap);
    }

    /**
     * {@code FAILED} is a signal, not a tombstone: the relay keeps coming back for the row, and a
     * broker that recovers hours later still gets the event without anyone touching the database.
     */
    @Test
    void aFailedEventKeepsBeingRetriedAndRecoversOnItsOwn() {
        outboxRepository.save(OutboxEvent.record("Order", "order-bad", "OrderCreated", "{}", OCCURRED_AT));
        eventPublisher.failFor("order-bad");

        RelayOutcome lastOutcome = null;
        for (int attempt = 1; attempt <= OutboxEvent.MAX_PUBLISH_ATTEMPTS; attempt++) {
            lastOutcome = relayService.relayDueEvents();
            clock.advanceBy(RetryBackoffPolicy.delayAfterAttempt(attempt));
        }

        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.FAILED);
        assertThat(attemptsOf("order-bad")).isEqualTo(OutboxEvent.MAX_PUBLISH_ATTEMPTS);
        assertThat(lastOutcome.failedCount()).isEqualTo(1);

        // Still in the rotation: the next due run attempts it again rather than ignoring it.
        RelayOutcome afterFailed = relayService.relayDueEvents();
        assertThat(afterFailed.failedCount()).isEqualTo(1);
        assertThat(attemptsOf("order-bad")).isEqualTo(OutboxEvent.MAX_PUBLISH_ATTEMPTS + 1);

        // And when the broker comes back, the row heals itself with no manual intervention.
        eventPublisher.stopFailing("order-bad");
        clock.advanceBy(RetryBackoffPolicy.delayAfterAttempt(attemptsOf("order-bad")));
        assertThat(relayService.relayDueEvents().publishedCount()).isEqualTo(1);
        assertThat(statusOf("order-bad")).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(eventOf("order-bad").nextAttemptAt()).isNull();
    }

    @Test
    void respectsTheRelayBatchSizeConstant() {
        for (int i = 0; i < OutboxRelayService.RELAY_BATCH_SIZE + 10; i++) {
            outboxRepository.save(OutboxEvent.record("Order", "order-" + i, "OrderCreated", "{}", OCCURRED_AT));
        }

        RelayOutcome outcome = relayService.relayDueEvents();

        assertThat(outcome.publishedCount()).isEqualTo(OutboxRelayService.RELAY_BATCH_SIZE);
    }

    private OutboxEvent eventOf(String aggregateId) {
        return outboxRepository.all().stream()
                .filter(event -> event.aggregateId().equals(aggregateId))
                .findFirst()
                .orElseThrow();
    }

    private OutboxStatus statusOf(String aggregateId) {
        return eventOf(aggregateId).status();
    }

    private int attemptsOf(String aggregateId) {
        return eventOf(aggregateId).publishAttempts();
    }
}

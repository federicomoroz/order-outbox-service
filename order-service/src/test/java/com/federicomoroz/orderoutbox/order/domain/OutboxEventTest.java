package com.federicomoroz.orderoutbox.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FAILED_AT = Instant.parse("2026-01-01T00:00:05Z");

    @Test
    void recordedEventStartsPendingWithNoAttempts() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);

        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.publishAttempts()).isZero();
        assertThat(event.publishedAt()).isNull();
        // No backoff has been earned yet, so the relay may take it on its very next poll.
        assertThat(event.nextAttemptAt()).isNull();
        assertThat(event.isDueAt(OCCURRED_AT)).isTrue();
        assertThat(event.aggregateType()).isEqualTo("Order");
        assertThat(event.aggregateId()).isEqualTo("order-1");
        assertThat(event.eventType()).isEqualTo("OrderCreated");
        assertThat(event.payload()).isEqualTo("{}");
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void markPublishedTransitionsToPublishedAndRecordsTimestamp() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);
        Instant publishedAt = Instant.parse("2026-01-01T00:00:02Z");

        event.markPublished(publishedAt);

        assertThat(event.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.publishedAt()).isEqualTo(publishedAt);
        assertThat(event.isDueAt(publishedAt)).isFalse();
    }

    @Test
    void publishingClearsAnyRetryDeadlineLeftBehindByEarlierFailures() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);
        event.recordFailedAttempt(FAILED_AT);
        assertThat(event.nextAttemptAt()).isNotNull();

        event.markPublished(Instant.parse("2026-01-01T00:00:10Z"));

        // A published row owes nothing; a leftover deadline would be a lie to whoever reads it.
        assertThat(event.nextAttemptAt()).isNull();
    }

    @Test
    void staysPendingWhileUnderMaxPublishAttempts() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);

        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS - 1; i++) {
            event.recordFailedAttempt(FAILED_AT);
            assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        }
        assertThat(event.publishAttempts()).isEqualTo(OutboxEvent.MAX_PUBLISH_ATTEMPTS - 1);
    }

    @Test
    void transitionsToFailedOnceMaxPublishAttemptsReached() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);

        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS; i++) {
            event.recordFailedAttempt(FAILED_AT);
        }

        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.publishAttempts()).isEqualTo(OutboxEvent.MAX_PUBLISH_ATTEMPTS);
    }

    @Test
    void aFailedEventStillHasAFutureAttemptScheduled_itIsDegradedNotAbandoned() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);
        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS; i++) {
            event.recordFailedAttempt(FAILED_AT);
        }
        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);

        // The whole point of the soft FAILED state: the relay is still going to come back for it.
        assertThat(event.nextAttemptAt()).isAfter(FAILED_AT);
        assertThat(event.isDueAt(FAILED_AT)).isFalse();
        assertThat(event.isDueAt(event.nextAttemptAt())).isTrue();

        // ...and it never stops being scheduled, however many more times it fails.
        for (int i = 0; i < 50; i++) {
            event.recordFailedAttempt(FAILED_AT);
            assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
            assertThat(event.nextAttemptAt()).isAfter(FAILED_AT);
        }
    }

    @Test
    void eachFailurePushesTheNextAttemptFurtherOut_untilTheCap() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);

        event.recordFailedAttempt(FAILED_AT);
        Instant afterFirst = event.nextAttemptAt();
        event.recordFailedAttempt(FAILED_AT);
        Instant afterSecond = event.nextAttemptAt();

        assertThat(afterSecond).isAfter(afterFirst);
        assertThat(afterFirst).isEqualTo(RetryBackoffPolicy.nextAttemptAfter(FAILED_AT, 1));
        assertThat(afterSecond).isEqualTo(RetryBackoffPolicy.nextAttemptAfter(FAILED_AT, 2));
    }

    @Test
    void anEventIsNotDueBeforeItsBackoffWindowElapses() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);
        event.recordFailedAttempt(FAILED_AT);

        assertThat(event.isDueAt(FAILED_AT)).isFalse();
        assertThat(event.isDueAt(event.nextAttemptAt().minusMillis(1))).isFalse();
        assertThat(event.isDueAt(event.nextAttemptAt())).isTrue();
        assertThat(event.isDueAt(event.nextAttemptAt().plusSeconds(1))).isTrue();
    }

    @Test
    void reconstituteRebuildsExactState() {
        OutboxEventId id = OutboxEventId.newId();
        Instant publishedAt = Instant.parse("2026-01-01T00:00:02Z");

        OutboxEvent event = OutboxEvent.reconstitute(id, "Order", "order-1", "OrderCreated", "{}", OCCURRED_AT,
                OutboxStatus.PUBLISHED, 1, publishedAt, null);

        assertThat(event.id()).isEqualTo(id);
        assertThat(event.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.publishAttempts()).isEqualTo(1);
        assertThat(event.publishedAt()).isEqualTo(publishedAt);
        assertThat(event.nextAttemptAt()).isNull();
    }

    @Test
    void reconstituteCarriesTheRetryDeadlineBackOutOfTheDatabase() {
        Instant nextAttemptAt = Instant.parse("2026-01-01T00:05:00Z");

        OutboxEvent event = OutboxEvent.reconstitute(OutboxEventId.newId(), "Order", "order-1", "OrderCreated",
                "{}", OCCURRED_AT, OutboxStatus.FAILED, OutboxEvent.MAX_PUBLISH_ATTEMPTS, null, nextAttemptAt);

        assertThat(event.nextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(event.isDueAt(nextAttemptAt.minusSeconds(1))).isFalse();
        assertThat(event.isDueAt(nextAttemptAt)).isTrue();
    }

    @Test
    void aRowThatPredatesTheBackoffColumnIsDueImmediately() {
        // V3 backfills next_attempt_at as NULL, so rows written before it must not be stranded.
        OutboxEvent event = OutboxEvent.reconstitute(OutboxEventId.newId(), "Order", "order-1", "OrderCreated",
                "{}", OCCURRED_AT, OutboxStatus.FAILED, OutboxEvent.MAX_PUBLISH_ATTEMPTS, null, null);

        assertThat(event.isDueAt(OCCURRED_AT)).isTrue();
    }
}

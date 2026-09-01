package com.federicomoroz.orderoutbox.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void recordedEventStartsPendingWithNoAttempts() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);

        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.publishAttempts()).isZero();
        assertThat(event.publishedAt()).isNull();
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
    }

    @Test
    void staysPendingWhileUnderMaxPublishAttempts() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);

        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS - 1; i++) {
            event.recordFailedAttempt();
            assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        }
        assertThat(event.publishAttempts()).isEqualTo(OutboxEvent.MAX_PUBLISH_ATTEMPTS - 1);
    }

    @Test
    void transitionsToFailedOnceMaxPublishAttemptsReached() {
        OutboxEvent event = OutboxEvent.record("Order", "order-1", "OrderCreated", "{}", OCCURRED_AT);

        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS; i++) {
            event.recordFailedAttempt();
        }

        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.publishAttempts()).isEqualTo(OutboxEvent.MAX_PUBLISH_ATTEMPTS);
    }

    @Test
    void reconstituteRebuildsExactState() {
        OutboxEventId id = OutboxEventId.newId();
        Instant publishedAt = Instant.parse("2026-01-01T00:00:02Z");

        OutboxEvent event = OutboxEvent.reconstitute(id, "Order", "order-1", "OrderCreated", "{}", OCCURRED_AT,
                OutboxStatus.PUBLISHED, 1, publishedAt);

        assertThat(event.id()).isEqualTo(id);
        assertThat(event.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.publishAttempts()).isEqualTo(1);
        assertThat(event.publishedAt()).isEqualTo(publishedAt);
    }
}

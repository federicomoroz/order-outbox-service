package com.federicomoroz.orderoutbox.order.adapter.in.web;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of one outbox row. The serialized {@code payload} is deliberately left out: it can
 * be arbitrarily large and an observer of the pattern cares about the row's <em>lifecycle</em>
 * (status, attempts, timings), not about the event body — that already travels over Kafka.
 *
 * <p>{@code nextAttemptAt} is part of that lifecycle and not an implementation detail: without it
 * a {@code FAILED} row reads as abandoned, when in fact the relay is still going to pick it up.
 * It is what lets the dashboard show a degraded row as self-healing rather than dead.
 */
public record OutboxEventResponse(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String status,
        int publishAttempts,
        Instant occurredAt,
        Instant publishedAt,
        /** When the relay may try this event again; {@code null} once published, or when due now. */
        Instant nextAttemptAt
) {

    public static OutboxEventResponse from(OutboxEvent event) {
        return new OutboxEventResponse(
                event.id().value(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.status().name(),
                event.publishAttempts(),
                event.occurredAt(),
                event.publishedAt(),
                event.nextAttemptAt());
    }
}

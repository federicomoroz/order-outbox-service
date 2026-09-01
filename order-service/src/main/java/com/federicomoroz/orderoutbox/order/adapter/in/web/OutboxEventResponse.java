package com.federicomoroz.orderoutbox.order.adapter.in.web;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of one outbox row. The serialized {@code payload} is deliberately left out: it can
 * be arbitrarily large and an observer of the pattern cares about the row's <em>lifecycle</em>
 * (status, attempts, timings), not about the event body — that already travels over Kafka.
 */
public record OutboxEventResponse(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String status,
        int publishAttempts,
        Instant occurredAt,
        Instant publishedAt
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
                event.publishedAt());
    }
}

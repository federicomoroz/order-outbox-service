package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxEventId;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;

/** Package-private, stateless. The only place that knows how to translate between
 * {@link OutboxEvent} and {@link OutboxEventJpaEntity}. */
final class OutboxEventMapper {

    private OutboxEventMapper() {
    }

    static OutboxEventJpaEntity toEntity(OutboxEvent event) {
        return new OutboxEventJpaEntity(
                event.id().value(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.status().name(),
                event.occurredAt(),
                event.publishAttempts(),
                event.publishedAt());
    }

    static OutboxEvent toDomain(OutboxEventJpaEntity entity) {
        return OutboxEvent.reconstitute(
                OutboxEventId.of(entity.getId()),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getOccurredAt(),
                OutboxStatus.valueOf(entity.getStatus()),
                entity.getPublishAttempts(),
                entity.getPublishedAt());
    }
}

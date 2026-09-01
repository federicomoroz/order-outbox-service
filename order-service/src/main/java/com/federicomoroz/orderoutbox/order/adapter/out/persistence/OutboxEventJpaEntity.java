package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA row shape for the {@code outbox_events} table. Package-private for the same reason as
 * {@link OrderJpaEntity}. */
@Entity
@Table(name = "outbox_events")
class OutboxEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventJpaEntity() {
        // required by JPA
    }

    OutboxEventJpaEntity(UUID id, String aggregateType, String aggregateId, String eventType, String payload,
                          String status, Instant occurredAt, int publishAttempts, Instant publishedAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.occurredAt = occurredAt;
        this.publishAttempts = publishAttempts;
        this.publishedAt = publishedAt;
    }

    UUID getId() {
        return id;
    }

    String getAggregateType() {
        return aggregateType;
    }

    String getAggregateId() {
        return aggregateId;
    }

    String getEventType() {
        return eventType;
    }

    String getPayload() {
        return payload;
    }

    String getStatus() {
        return status;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    int getPublishAttempts() {
        return publishAttempts;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }
}

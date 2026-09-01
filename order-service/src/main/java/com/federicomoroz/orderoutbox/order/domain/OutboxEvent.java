package com.federicomoroz.orderoutbox.order.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A domain fact recorded for later relay to Kafka, written in the very same DB transaction as
 * the aggregate that produced it — the core of the Transactional Outbox pattern.
 *
 * <p>Deliberately mutable, unlike {@link Order}: an {@code OutboxEvent} has a real lifecycle
 * (PENDING -&gt; PUBLISHED, or PENDING -&gt; FAILED after repeated publish failures) that reads
 * naturally as state transitions on one identity, rather than as a fresh immutable value per
 * transition. The contrast with {@code Order} is intentional, not inconsistent.
 */
public final class OutboxEvent {

    /** After this many failed publish attempts, the relay stops retrying the event automatically. */
    public static final int MAX_PUBLISH_ATTEMPTS = 5;

    private final OutboxEventId id;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final String payload;
    private final Instant occurredAt;
    private OutboxStatus status;
    private int publishAttempts;
    private Instant publishedAt;

    private OutboxEvent(OutboxEventId id, String aggregateType, String aggregateId, String eventType,
                         String payload, Instant occurredAt, OutboxStatus status, int publishAttempts,
                         Instant publishedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.aggregateType = requireNonBlank(aggregateType, "aggregateType");
        this.aggregateId = requireNonBlank(aggregateId, "aggregateId");
        this.eventType = requireNonBlank(eventType, "eventType");
        this.payload = requireNonBlank(payload, "payload");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.publishAttempts = publishAttempts;
        this.publishedAt = publishedAt;
    }

    /** Records a brand-new domain fact, always starting life as {@link OutboxStatus#PENDING}. */
    public static OutboxEvent record(String aggregateType, String aggregateId, String eventType, String payload,
                                      Instant occurredAt) {
        return recordWithId(OutboxEventId.newId(), aggregateType, aggregateId, eventType, payload, occurredAt);
    }

    /**
     * Same as {@link #record}, but with the id supplied by the caller instead of generated here.
     * Needed when the id must also be embedded in the serialized payload itself — e.g. so a
     * downstream idempotent consumer has a stable event identity to deduplicate on, independent
     * of any business key. See {@code CreateOrderService}.
     */
    public static OutboxEvent recordWithId(OutboxEventId id, String aggregateType, String aggregateId,
                                            String eventType, String payload, Instant occurredAt) {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, occurredAt,
                OutboxStatus.PENDING, 0, null);
    }

    /** Rebuilds an {@code OutboxEvent} from persisted state. Used only by the persistence mapper. */
    public static OutboxEvent reconstitute(OutboxEventId id, String aggregateType, String aggregateId,
                                            String eventType, String payload, Instant occurredAt,
                                            OutboxStatus status, int publishAttempts, Instant publishedAt) {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, occurredAt, status,
                publishAttempts, publishedAt);
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }

    /** Records one failed publish attempt; transitions to {@link OutboxStatus#FAILED} once
     * {@link #MAX_PUBLISH_ATTEMPTS} is reached, otherwise stays {@link OutboxStatus#PENDING} for retry. */
    public void recordFailedAttempt() {
        this.publishAttempts++;
        this.status = (publishAttempts >= MAX_PUBLISH_ATTEMPTS) ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public OutboxEventId id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public String payload() {
        return payload;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public OutboxStatus status() {
        return status;
    }

    public int publishAttempts() {
        return publishAttempts;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutboxEvent other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

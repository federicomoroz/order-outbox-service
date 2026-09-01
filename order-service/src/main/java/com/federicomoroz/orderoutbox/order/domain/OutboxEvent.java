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
 *
 * <p>Every one of those transitions is decided <em>here</em>. A failed publish does not just bump
 * a counter: it also fixes, via {@link RetryBackoffPolicy}, the instant the event may next be
 * tried. Attempt count, status and that instant move together or not at all, which is precisely
 * why they are computed in the domain and merely persisted by the adapter — split across a SQL
 * {@code UPDATE} and a service, they would drift.
 */
public final class OutboxEvent {

    /**
     * After this many failed publish attempts the event is flagged {@link OutboxStatus#FAILED}.
     * That is a <em>signal</em>, not a tombstone: the relay keeps retrying past it, forever, at
     * the capped {@link RetryBackoffPolicy} interval. Nothing about an outbox row is ever
     * abandoned, because re-publishing costs nothing downstream — the consumer is idempotent.
     */
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
    private Instant nextAttemptAt;

    private OutboxEvent(OutboxEventId id, String aggregateType, String aggregateId, String eventType,
                         String payload, Instant occurredAt, OutboxStatus status, int publishAttempts,
                         Instant publishedAt, Instant nextAttemptAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.aggregateType = requireNonBlank(aggregateType, "aggregateType");
        this.aggregateId = requireNonBlank(aggregateId, "aggregateId");
        this.eventType = requireNonBlank(eventType, "eventType");
        this.payload = requireNonBlank(payload, "payload");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.publishAttempts = publishAttempts;
        this.publishedAt = publishedAt;
        this.nextAttemptAt = nextAttemptAt;
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
                OutboxStatus.PENDING, 0, null, null);
    }

    /** Rebuilds an {@code OutboxEvent} from persisted state. Used only by the persistence mapper. */
    public static OutboxEvent reconstitute(OutboxEventId id, String aggregateType, String aggregateId,
                                            String eventType, String payload, Instant occurredAt,
                                            OutboxStatus status, int publishAttempts, Instant publishedAt,
                                            Instant nextAttemptAt) {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, occurredAt, status,
                publishAttempts, publishedAt, nextAttemptAt);
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        // Nothing is owed to this row any more; leaving a stale retry deadline behind would be a lie.
        this.nextAttemptAt = null;
    }

    /**
     * Records one failed publish attempt <em>and</em> the backoff it earns. Flags the event
     * {@link OutboxStatus#FAILED} once {@link #MAX_PUBLISH_ATTEMPTS} is reached, but keeps
     * scheduling a next attempt either way — a {@code FAILED} event is degraded, never dropped.
     *
     * @param failedAt when the publish attempt failed; the backoff window is measured from here
     */
    public void recordFailedAttempt(Instant failedAt) {
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        this.publishAttempts++;
        this.status = (publishAttempts >= MAX_PUBLISH_ATTEMPTS) ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        this.nextAttemptAt = RetryBackoffPolicy.nextAttemptAfter(failedAt, publishAttempts);
    }

    /**
     * Whether the relay may attempt this event at {@code now}: still awaiting relay, and past its
     * backoff window. A never-attempted event has no {@code nextAttemptAt} and is due immediately.
     */
    public boolean isDueAt(Instant now) {
        return OutboxStatus.awaitingRelay().contains(status)
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
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

    /** Earliest instant the relay may retry this event; {@code null} means "due now". */
    public Instant nextAttemptAt() {
        return nextAttemptAt;
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

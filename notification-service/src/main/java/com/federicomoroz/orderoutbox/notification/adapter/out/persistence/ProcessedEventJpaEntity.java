package com.federicomoroz.orderoutbox.notification.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA row shape for the {@code processed_events} table. {@code eventId} is the primary key —
 * that constraint, not any application code, is what makes "process each event exactly once"
 * a real guarantee rather than a hopeful convention.
 *
 * <p>Only mapped for reads/reconstruction; the actual idempotent insert goes through
 * {@code ProcessedEventJpaRepository#tryInsert}, a native {@code ON CONFLICT DO NOTHING} query —
 * see that interface's Javadoc for why a normal JPA {@code save()} would not be safe here.
 */
@Entity
@Table(name = "processed_events")
class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {
        // required by JPA
    }

    ProcessedEventJpaEntity(UUID eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    UUID getEventId() {
        return eventId;
    }

    Instant getProcessedAt() {
        return processedAt;
    }
}

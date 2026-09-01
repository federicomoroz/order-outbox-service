package com.federicomoroz.orderoutbox.notification.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The idempotency record itself, modeled as a first-class domain concept rather than a bare
 * persistence detail — it IS the fact "this event has already been handled". Its uniqueness (a
 * primary key on {@code eventId} in the persistence adapter) is what turns that fact into a real
 * guarantee rather than a documented intention.
 */
public record ProcessedEvent(UUID eventId, Instant processedAt) {

    public ProcessedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(processedAt, "processedAt must not be null");
    }

    public static ProcessedEvent of(UUID eventId, Clock clock) {
        return new ProcessedEvent(eventId, Instant.now(clock));
    }
}

package com.federicomoroz.orderoutbox.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessedEventTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void ofStampsTheCurrentTimeFromTheGivenClock() {
        UUID eventId = UUID.randomUUID();

        ProcessedEvent event = ProcessedEvent.of(eventId, FIXED_CLOCK);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.processedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void rejectsNullEventId() {
        assertThatThrownBy(() -> new ProcessedEvent(null, Instant.now(FIXED_CLOCK)))
                .isInstanceOf(NullPointerException.class);
    }
}

package com.federicomoroz.orderoutbox.notification.adapter.out.persistence;

import com.federicomoroz.orderoutbox.notification.domain.ProcessedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code tryMarkProcessed}'s idempotency guarantee is real, not just documented: the
 * second call for the same {@code eventId} must return {@code false} because a real Postgres
 * {@code PRIMARY KEY} constraint on {@code processed_events.event_id} rejects the duplicate —
 * each call here runs in its own transaction (Spring Data JPA's default per-method transactional
 * behavior on the repository proxy), so this genuinely proves cross-transaction, DB-level
 * uniqueness, not same-session visibility.
 */
@SpringBootTest(classes = ProcessedEventPersistenceAdapterIT.PersistenceTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProcessedEventPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private ProcessedEventPersistenceAdapter processedEventPersistenceAdapter;

    @Test
    void secondTryMarkProcessedForTheSameEventIdReturnsFalse_dbConstraintEnforcesIt() {
        UUID eventId = UUID.randomUUID();

        boolean first = processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(eventId, FIXED_CLOCK));
        boolean second = processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(eventId, FIXED_CLOCK));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void differentEventIdsAreEachMarkedProcessedSuccessfully() {
        boolean first =
                processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(UUID.randomUUID(), FIXED_CLOCK));
        boolean second =
                processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(UUID.randomUUID(), FIXED_CLOCK));

        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    @SpringBootApplication
    static class PersistenceTestConfig {
    }
}

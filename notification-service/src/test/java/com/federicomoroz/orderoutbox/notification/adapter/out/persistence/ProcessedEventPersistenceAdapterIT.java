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
 * {@code PRIMARY KEY} constraint on {@code processed_events.event_id} rejects the duplicate.
 *
 * <p>Each call goes through {@link SpringTransactionRunner} — exactly how
 * {@code HandleOrderCreatedEventService} really invokes this adapter — with each call getting
 * its own separate, real, committed transaction. That's deliberate: the underlying
 * {@code @Modifying} native query requires an active JPA transaction to execute at all (calling
 * the adapter with no surrounding transaction throws {@code TransactionRequiredException}), and
 * running each attempt in its own transaction is what proves this is a true cross-transaction,
 * DB-level uniqueness guarantee — not same-session/same-transaction visibility.
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

    @Autowired
    private SpringTransactionRunner transactionRunner;

    @Test
    void secondTryMarkProcessedForTheSameEventIdReturnsFalse_dbConstraintEnforcesIt() {
        UUID eventId = UUID.randomUUID();

        boolean first = transactionRunner.run(() ->
                processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(eventId, FIXED_CLOCK)));
        boolean second = transactionRunner.run(() ->
                processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(eventId, FIXED_CLOCK)));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void differentEventIdsAreEachMarkedProcessedSuccessfully() {
        boolean first = transactionRunner.run(() ->
                processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(UUID.randomUUID(), FIXED_CLOCK)));
        boolean second = transactionRunner.run(() ->
                processedEventPersistenceAdapter.tryMarkProcessed(ProcessedEvent.of(UUID.randomUUID(), FIXED_CLOCK)));

        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    @SpringBootApplication
    static class PersistenceTestConfig {
    }
}

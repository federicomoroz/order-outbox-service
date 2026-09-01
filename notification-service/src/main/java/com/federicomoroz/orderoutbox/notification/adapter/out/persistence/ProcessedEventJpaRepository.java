package com.federicomoroz.orderoutbox.notification.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Package-private on purpose — only {@link ProcessedEventPersistenceAdapter} may see raw JPA entities. */
interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {

    /**
     * A single native INSERT guarded by {@code ON CONFLICT DO NOTHING} on the {@code event_id}
     * primary key. Returns the number of rows actually inserted: 1 the first time an eventId is
     * seen, 0 on every later (duplicate) delivery.
     *
     * <p>Deliberately not "catch the constraint violation thrown by a normal JPA
     * {@code save()}+flush": {@code HandleOrderCreatedEventService} runs the idempotency check
     * and the {@code Notification} save in the very same transaction, and a
     * {@code ConstraintViolationException} raised mid-flush leaves the Hibernate persistence
     * context unusable for the rest of that transaction — which would break the one-transaction
     * atomicity this whole use case depends on. {@code ON CONFLICT DO NOTHING} lets the same DB
     * constraint do its job as an ordinary, exception-free DML statement instead.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, :processedAt) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int tryInsert(@Param("eventId") UUID eventId, @Param("processedAt") Instant processedAt);
}

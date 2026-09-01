package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Package-private on purpose — only {@link OutboxEventPersistenceAdapter} may see raw JPA entities. */
interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * The relay's "find work to do" query. Two conditions, both required: the row must still be
     * awaiting relay, and its backoff window must have elapsed — a {@code NULL next_attempt_at}
     * meaning "never attempted, due immediately".
     *
     * <p>Which statuses count as "awaiting relay" is not decided here: the caller passes them in
     * from {@code OutboxStatus.awaitingRelay()}, so the SQL never becomes a second, divergent
     * definition of the domain's lifecycle. It does have to match the predicate of
     * {@code idx_outbox_events_due} for that partial index to be usable, which is why the
     * migration and this query are edited together.
     */
    @Query("""
            SELECT e FROM OutboxEventJpaEntity e
            WHERE e.status IN :statuses
              AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now)
            ORDER BY e.occurredAt ASC
            """)
    List<OutboxEventJpaEntity> findDue(@Param("statuses") Collection<String> statuses,
                                        @Param("now") Instant now,
                                        Pageable pageable);

    List<OutboxEventJpaEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);
}

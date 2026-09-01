package com.federicomoroz.orderoutbox.order.application.service;

import com.federicomoroz.orderoutbox.order.application.port.in.RelayOutboxEventsUseCase;
import com.federicomoroz.orderoutbox.order.application.port.out.EventPublisherPort;
import com.federicomoroz.orderoutbox.order.application.port.out.OutboxRepository;
import com.federicomoroz.orderoutbox.order.application.port.out.TransactionRunner;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.domain.OutboxStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Plain Java class driven by {@code OutboxRelayScheduler} on a fixed delay.
 *
 * <p>Golden rule enforced here: never perform network I/O inside an open DB transaction. Publish
 * to Kafka happens with no transaction open at all; each event's resulting status change is then
 * persisted in its own short, separate transaction. That means if event #3 in a batch fails,
 * events #1 and #2 — already marked {@code PUBLISHED} in their own committed transactions — are
 * not rolled back.
 *
 * <p>This service decides <em>nothing</em> about retries. It asks the repository which events are
 * due at this instant, publishes them, and tells each {@link OutboxEvent} how it went; the event
 * itself works out its new status and when it may be tried again. So "no event is ever abandoned"
 * is a property of the domain model, not of a scheduling loop that could be rewritten around it.
 */
public final class OutboxRelayService implements RelayOutboxEventsUseCase {

    static final int RELAY_BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final EventPublisherPort eventPublisherPort;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public OutboxRelayService(OutboxRepository outboxRepository, EventPublisherPort eventPublisherPort,
                               TransactionRunner transactionRunner, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.eventPublisherPort = eventPublisherPort;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    @Override
    public RelayOutcome relayDueEvents() {
        List<OutboxEvent> due = outboxRepository.findDueBatch(RELAY_BATCH_SIZE, Instant.now(clock));

        int publishedCount = 0;
        int failedCount = 0;
        int retriedCount = 0;

        for (OutboxEvent event : due) {
            if (publishOne(event)) {
                publishedCount++;
            } else if (event.status() == OutboxStatus.FAILED) {
                failedCount++;
            } else {
                retriedCount++;
            }
        }

        return new RelayOutcome(publishedCount, failedCount, retriedCount);
    }

    /** Publishes a single event and persists the resulting state transition. Returns true on success. */
    private boolean publishOne(OutboxEvent event) {
        try {
            eventPublisherPort.publish(event);
            event.markPublished(Instant.now(clock));
            transactionRunner.run(() -> outboxRepository.save(event));
            return true;
        } catch (RuntimeException publishFailure) {
            // Read the clock again rather than reusing the batch's instant: the publish attempt
            // itself can take seconds, and the backoff window starts when it actually failed.
            event.recordFailedAttempt(Instant.now(clock));
            transactionRunner.run(() -> outboxRepository.save(event));
            return false;
        }
    }
}

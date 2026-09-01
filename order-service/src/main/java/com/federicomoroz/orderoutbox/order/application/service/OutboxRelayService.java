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
    public RelayOutcome relayPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findPendingBatch(RELAY_BATCH_SIZE);

        int publishedCount = 0;
        int failedCount = 0;
        int retriedCount = 0;

        for (OutboxEvent event : pending) {
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
            event.recordFailedAttempt();
            transactionRunner.run(() -> outboxRepository.save(event));
            return false;
        }
    }
}

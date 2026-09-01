package com.federicomoroz.orderoutbox.notification.application.service;

import com.federicomoroz.orderoutbox.notification.application.port.in.HandleOrderCreatedEventCommand;
import com.federicomoroz.orderoutbox.notification.application.port.in.HandleOrderCreatedEventUseCase;
import com.federicomoroz.orderoutbox.notification.application.port.out.NotificationRepository;
import com.federicomoroz.orderoutbox.notification.application.port.out.ProcessedEventRepository;
import com.federicomoroz.orderoutbox.notification.application.port.out.TransactionRunner;
import com.federicomoroz.orderoutbox.notification.domain.Notification;
import com.federicomoroz.orderoutbox.notification.domain.ProcessedEvent;

import java.time.Clock;

/**
 * A plain Java class — no Spring, no Kafka. If {@link ProcessedEventRepository#tryMarkProcessed}
 * reports a duplicate, this returns {@code false} without touching {@link NotificationRepository}
 * at all: no error, just a no-op. If anything fails while inside the transaction, the whole thing
 * (including the idempotency marker) rolls back, the exception propagates to the Kafka adapter,
 * and the consumed offset is never committed — so the broker redelivers the message and this
 * class gets an honest second chance at it.
 */
public final class HandleOrderCreatedEventService implements HandleOrderCreatedEventUseCase {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public HandleOrderCreatedEventService(ProcessedEventRepository processedEventRepository,
                                           NotificationRepository notificationRepository,
                                           TransactionRunner transactionRunner, Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    @Override
    public boolean handle(HandleOrderCreatedEventCommand command) {
        return transactionRunner.run(() -> {
            boolean firstTimeSeen = processedEventRepository.tryMarkProcessed(
                    ProcessedEvent.of(command.eventId(), clock));

            if (!firstTimeSeen) {
                return false;
            }

            Notification notification = Notification.forOrderCreated(
                    command.orderId(), command.customerId(), command.productId(), command.quantity(), clock);
            notificationRepository.save(notification);
            return true;
        });
    }
}

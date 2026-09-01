package com.federicomoroz.orderoutbox.notification.application.service;

import com.federicomoroz.orderoutbox.notification.application.port.in.HandleOrderCreatedEventCommand;
import com.federicomoroz.orderoutbox.notification.application.port.out.NotificationRepository;
import com.federicomoroz.orderoutbox.notification.domain.Notification;
import com.federicomoroz.orderoutbox.notification.testsupport.InMemoryNotificationRepository;
import com.federicomoroz.orderoutbox.notification.testsupport.InMemoryProcessedEventRepository;
import com.federicomoroz.orderoutbox.notification.testsupport.InMemoryTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandleOrderCreatedEventServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final Instant OCCURRED_AT = Instant.parse("2025-12-31T23:59:00Z");

    private InMemoryProcessedEventRepository processedEventRepository;
    private InMemoryNotificationRepository notificationRepository;
    private HandleOrderCreatedEventService service;

    @BeforeEach
    void setUp() {
        processedEventRepository = new InMemoryProcessedEventRepository();
        notificationRepository = new InMemoryNotificationRepository();
        service = new HandleOrderCreatedEventService(processedEventRepository, notificationRepository,
                new InMemoryTransactionRunner(), FIXED_CLOCK);
    }

    private static HandleOrderCreatedEventCommand commandFor(UUID eventId) {
        return new HandleOrderCreatedEventCommand(eventId, UUID.randomUUID(), UUID.randomUUID(), "sku-1", 2,
                new BigDecimal("9.99"), "USD", OCCURRED_AT);
    }

    @Test
    void firstDeliveryIsProcessedAndCreatesOneNotification() {
        UUID eventId = UUID.randomUUID();

        boolean processed = service.handle(commandFor(eventId));

        assertThat(processed).isTrue();
        assertThat(notificationRepository.size()).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryOfTheSameEventIdIsSkippedWithoutCreatingASecondNotification() {
        UUID eventId = UUID.randomUUID();
        HandleOrderCreatedEventCommand command = commandFor(eventId);

        boolean first = service.handle(command);
        boolean second = service.handle(command);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(notificationRepository.size()).isEqualTo(1);
    }

    @Test
    void differentEventIdsEachProduceTheirOwnNotification() {
        service.handle(commandFor(UUID.randomUUID()));
        service.handle(commandFor(UUID.randomUUID()));

        assertThat(notificationRepository.size()).isEqualTo(2);
    }

    @Test
    void notificationCarriesTheOrderAndCustomerFromTheCommand() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        HandleOrderCreatedEventCommand command = new HandleOrderCreatedEventCommand(
                eventId, orderId, customerId, "sku-9", 5, new BigDecimal("1.00"), "USD", OCCURRED_AT);

        service.handle(command);

        Notification notification = notificationRepository.findAll().get(0);
        assertThat(notification.orderId()).isEqualTo(orderId);
        assertThat(notification.customerId()).isEqualTo(customerId);
    }

    @Test
    void aFailureWhileSavingTheNotificationPropagatesInsteadOfBeingSwallowed() {
        // Real atomicity (the processed-event marker also being rolled back) is proven against a
        // real Postgres transaction by OrderCreatedEventConsumerIT, not by this in-memory fake.
        // What matters at this layer, and what this test proves: the failure must reach the
        // caller (the Kafka listener) so it does not ack the offset — never be swallowed here.
        NotificationRepository explodingNotificationRepository = new NotificationRepository() {
            @Override
            public void save(Notification notification) {
                throw new RuntimeException("simulated failure while saving the notification");
            }

            @Override
            public List<Notification> findAll() {
                return List.of();
            }
        };
        HandleOrderCreatedEventService faultyService = new HandleOrderCreatedEventService(
                processedEventRepository, explodingNotificationRepository, new InMemoryTransactionRunner(), FIXED_CLOCK);

        assertThatThrownBy(() -> faultyService.handle(commandFor(UUID.randomUUID())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated failure");
    }
}

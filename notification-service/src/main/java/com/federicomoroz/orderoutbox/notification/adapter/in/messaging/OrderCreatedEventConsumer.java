package com.federicomoroz.orderoutbox.notification.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.federicomoroz.orderoutbox.notification.application.port.in.HandleOrderCreatedEventCommand;
import com.federicomoroz.orderoutbox.notification.application.port.in.HandleOrderCreatedEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Deserializes Jackson directly in this adapter — unlike {@code order-service}, which hides
 * Jackson behind an {@code EventSerializer} port. The two situations are not symmetric: on the
 * publish side, {@code application/} builds the outbox payload as part of its own business logic
 * (so Jackson had to be pushed behind a port to keep that logic framework-free). Here, this
 * method does nothing but translate an external wire form into an internal command — pure
 * adapter-layer translation, the textbook job of an inbound adapter — so there is no purity to
 * protect and no reason to introduce a port for it.
 */
@Component
public class OrderCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventConsumer.class);

    private final HandleOrderCreatedEventUseCase handleOrderCreatedEventUseCase;
    private final ObjectMapper objectMapper;

    public OrderCreatedEventConsumer(HandleOrderCreatedEventUseCase handleOrderCreatedEventUseCase,
                                      ObjectMapper objectMapper) {
        this.handleOrderCreatedEventUseCase = handleOrderCreatedEventUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED_TOPIC, groupId = "notification-service")
    public void onMessage(String payload) {
        OrderCreatedEventPayload event = deserialize(payload);

        HandleOrderCreatedEventCommand command = new HandleOrderCreatedEventCommand(
                event.eventId(),
                event.orderId(),
                event.customerId(),
                event.productId(),
                event.quantity(),
                event.unitPriceAmount(),
                event.unitPriceCurrency(),
                event.occurredAt());

        boolean processed = handleOrderCreatedEventUseCase.handle(command);
        if (!processed) {
            log.info("Skipped duplicate OrderCreated event, eventId={}", event.eventId());
        }
    }

    private OrderCreatedEventPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, OrderCreatedEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new EventDeserializationException("failed to deserialize OrderCreatedEvent payload", e);
        }
    }
}

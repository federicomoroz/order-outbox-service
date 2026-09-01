package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.federicomoroz.orderoutbox.order.application.port.out.EventSerializer;
import com.federicomoroz.orderoutbox.order.domain.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;

/**
 * The only place in this module that imports Jackson. {@code application/} depends on
 * {@link EventSerializer} instead of on this class directly — verified by ArchUnit.
 */
@Component
public class JacksonEventSerializer implements EventSerializer {

    private final ObjectMapper objectMapper;

    public JacksonEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serialize(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("failed to serialize OrderCreatedEvent for order " + event.orderId(), e);
        }
    }
}

package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

import com.federicomoroz.orderoutbox.order.application.port.out.EventPublisherPort;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Blocking on purpose: {@code KafkaTemplate.send(...).get(timeoutMs)} is called synchronously
 * because the outbox relay is not on the HTTP hot path — there is no request thread waiting on
 * this call, so trading a little throughput for simplicity (know immediately whether the publish
 * succeeded) is the right call here.
 */
@Component
public class KafkaEventPublisher implements EventPublisherPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long publishTimeoutMs;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${outbox.relay.publish-timeout-ms:5000}") long publishTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeoutMs = publishTimeoutMs;
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.ORDER_CREATED_TOPIC, event.aggregateId(), event.payload())
                    .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("interrupted while publishing outbox event " + event.id(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishException("failed to publish outbox event " + event.id(), e);
        }
    }
}

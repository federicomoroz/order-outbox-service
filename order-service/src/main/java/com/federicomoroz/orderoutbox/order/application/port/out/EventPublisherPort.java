package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

/**
 * Secondary port for publishing an already-serialized outbox event to the broker. Used
 * exclusively by the outbox relay — never by the HTTP request path, which must return to the
 * caller without waiting on any network call to Kafka.
 */
public interface EventPublisherPort {

    void publish(OutboxEvent event);
}

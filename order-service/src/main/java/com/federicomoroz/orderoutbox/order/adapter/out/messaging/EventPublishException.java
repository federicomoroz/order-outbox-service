package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

/** Wraps any failure to hand an event off to Kafka (timeout, broker unreachable, etc.), so
 * {@code OutboxRelayService} can catch a single well-known unchecked exception type. */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.federicomoroz.orderoutbox.notification.adapter.in.messaging;

/** A malformed message on {@code order.created.v1} — a poison pill. Left to propagate so
 * Spring Kafka's {@code DefaultErrorHandler}/{@code DeadLetterPublishingRecoverer} can route it
 * to the dead-letter topic instead of blocking the partition forever. */
public class EventDeserializationException extends RuntimeException {

    public EventDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

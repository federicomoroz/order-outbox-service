package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

/** Wraps a Jackson serialization failure behind an unchecked exception owned by this adapter. */
public class EventSerializationException extends RuntimeException {

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

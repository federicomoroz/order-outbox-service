package com.federicomoroz.orderoutbox.order.domain;

/** Thrown when an {@link Order} is constructed with data that violates a domain invariant. */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}

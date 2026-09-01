package com.federicomoroz.orderoutbox.notification.application.port.in;

/** Primary port driven by {@code OrderCreatedEventConsumer} for every message read off
 * {@code order.created.v1} — including redeliveries of a message already handled. */
public interface HandleOrderCreatedEventUseCase {

    /**
     * @return {@code true} if this call actually processed the event for the first time,
     *         {@code false} if it was a duplicate delivery that was safely skipped.
     */
    boolean handle(HandleOrderCreatedEventCommand command);
}

package com.federicomoroz.orderoutbox.order.application.port.out;

import com.federicomoroz.orderoutbox.order.domain.event.OrderCreatedEvent;

/**
 * Secondary port that turns a domain event into its wire representation (JSON, via the Jackson
 * adapter). Exists explicitly so {@code application/} never imports Jackson directly — a rule
 * ArchUnit verifies, on top of the {@code org.springframework}/{@code jakarta.persistence}/
 * {@code org.hibernate} bans that apply to both {@code domain} and {@code application}.
 */
public interface EventSerializer {

    String serialize(OrderCreatedEvent event);
}

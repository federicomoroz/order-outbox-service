package com.federicomoroz.orderoutbox.notification.adapter.in.messaging;

/** This module's own copy of the topic name — no shared constants module between services on
 * purpose (see README "Decisiones puntuales" for why there is no shared DTO/constants JAR). */
final class KafkaTopics {

    static final String ORDER_CREATED_TOPIC = "order.created.v1";

    private KafkaTopics() {
    }
}

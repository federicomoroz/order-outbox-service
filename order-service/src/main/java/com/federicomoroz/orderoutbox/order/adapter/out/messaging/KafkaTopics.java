package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

/** Central registry of topic names this service publishes to. Versioned in the name
 * ({@code .v1}) so a future breaking change to the payload shape can ship as a new topic
 * without an in-place migration. */
public final class KafkaTopics {

    public static final String ORDER_CREATED_TOPIC = "order.created.v1";

    private KafkaTopics() {
    }
}

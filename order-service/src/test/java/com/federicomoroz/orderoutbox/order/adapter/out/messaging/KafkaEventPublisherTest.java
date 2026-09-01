package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the invariant that the original bug violated: the relay's local wait must be strictly
 * larger than the producer's own {@code delivery.timeout.ms}, so the producer's verdict always
 * arrives first. Two independent numbers in two different files will eventually be edited apart,
 * so the relationship is checked at startup rather than trusted.
 *
 * <p>No broker involved: building a {@link KafkaTemplate} connects to nothing, and the check
 * reads the producer factory's configuration rather than an open connection.
 */
class KafkaEventPublisherTest {

    private static final long DELIVERY_TIMEOUT_MS = 8000L;

    @Test
    void startsWhenTheLocalWaitIsStrictlyLargerThanTheProducerDeadline() {
        assertThatCode(() -> new KafkaEventPublisher(templateWithDeliveryTimeout(DELIVERY_TIMEOUT_MS), 10_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhenTheLocalWaitWouldFireFirst() {
        // This is exactly the shipped bug: 5s local wait against a producer still retrying.
        assertThatThrownBy(() -> new KafkaEventPublisher(templateWithDeliveryTimeout(DELIVERY_TIMEOUT_MS), 5000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery.timeout.ms");
    }

    @Test
    void refusesToStartWhenTheTwoDeadlinesAreEqual() {
        // Equal is not good enough: which of the two fires first becomes a race.
        assertThatThrownBy(() -> new KafkaEventPublisher(
                templateWithDeliveryTimeout(DELIVERY_TIMEOUT_MS), DELIVERY_TIMEOUT_MS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnconfiguredProducerFallsBackToTheKafkaClientDefault_notToSilentAgreement() {
        // delivery.timeout.ms absent means the client will use 120s. A 10s local wait against
        // that is the original bug, so it must fail rather than quietly look correct.
        assertThatThrownBy(() -> new KafkaEventPublisher(templateWithoutDeliveryTimeout(), 10_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("120000");
    }

    private static KafkaTemplate<String, String> templateWithDeliveryTimeout(long deliveryTimeoutMs) {
        Map<String, Object> config = baseProducerConfig();
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) deliveryTimeoutMs);
        return new KafkaTemplate<>(producerFactory(config));
    }

    private static KafkaTemplate<String, String> templateWithoutDeliveryTimeout() {
        return new KafkaTemplate<>(producerFactory(baseProducerConfig()));
    }

    private static ProducerFactory<String, String> producerFactory(Map<String, Object> config) {
        return new DefaultKafkaProducerFactory<>(config);
    }

    private static Map<String, Object> baseProducerConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return config;
    }
}

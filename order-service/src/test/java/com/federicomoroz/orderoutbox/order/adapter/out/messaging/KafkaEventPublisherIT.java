package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka real (Testcontainers): publishes through the actual adapter, then verifies with a plain
 * {@code KafkaConsumer} — no {@code @KafkaListener}, no Spring Kafka test scaffolding — to prove
 * the message really lands on the wire in the shape the relay promises.
 *
 * <p>Scoped to just the messaging package: JPA/DataSource/Flyway auto-configuration is excluded
 * because this test needs neither a database nor the composition root, only
 * {@link KafkaEventPublisher} and a real broker.
 */
@SpringBootTest(classes = KafkaEventPublisherIT.MessagingTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class KafkaEventPublisherIT {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    private KafkaEventPublisher kafkaEventPublisher;

    @Test
    void publishesToKafkaAndAPlainConsumerReceivesTheExactPayload() {
        OutboxEvent event = OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"hello\":\"world\"}", Instant.now());

        kafkaEventPublisher.publish(event);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-plain-consumer-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(KafkaTopics.ORDER_CREATED_TOPIC));

            ConsumerRecord<String, String> received = pollForOneRecord(consumer, Duration.ofSeconds(15));

            assertThat(received.key()).isEqualTo(event.aggregateId());
            assertThat(received.value()).isEqualTo(event.payload());
        }
    }

    private ConsumerRecord<String, String> pollForOneRecord(KafkaConsumer<String, String> consumer, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("no record received on " + KafkaTopics.ORDER_CREATED_TOPIC + " within " + timeout);
    }

    @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class})
    static class MessagingTestConfig {
    }
}

package com.federicomoroz.orderoutbox.notification.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.federicomoroz.orderoutbox.notification.NotificationServiceApplication;
import com.federicomoroz.orderoutbox.notification.application.port.out.NotificationRepository;
import com.federicomoroz.orderoutbox.notification.domain.Notification;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The most important test in the repo: produces the exact same message twice — same
 * {@code eventId}, simulating a Kafka redelivery — and confirms exactly one row lands in
 * {@code notifications}. Loads the real {@link NotificationServiceApplication} (composition root
 * included) against real Postgres and Kafka containers, so the whole idempotent-consumer path —
 * {@code @KafkaListener} -> {@code HandleOrderCreatedEventService} ->
 * {@code tryMarkProcessed}'s DB constraint -> {@code Notification} write — runs for real, not
 * through fakes.
 */
@SpringBootTest(classes = NotificationServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OrderCreatedEventConsumerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void duplicateDeliveryOfTheSameEventProducesExactlyOneNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        OrderCreatedEventPayload payload = new OrderCreatedEventPayload(
                eventId, UUID.randomUUID(), UUID.randomUUID(), "sku-dup", 1,
                new BigDecimal("5.00"), "USD", Instant.now());

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = objectMapper.writeValueAsString(payload);

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            // Same key, same payload, sent twice: a real-world redelivery, not a contrived duplicate.
            producer.send(new ProducerRecord<>(KafkaTopics.ORDER_CREATED_TOPIC, payload.orderId().toString(), json))
                    .get();
            producer.send(new ProducerRecord<>(KafkaTopics.ORDER_CREATED_TOPIC, payload.orderId().toString(), json))
                    .get();
        }

        List<Notification> notifications = waitForNotifications(1, Duration.ofSeconds(20));

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).orderId()).isEqualTo(payload.orderId());
    }

    private List<Notification> waitForNotifications(int expectedCount, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        List<Notification> notifications = notificationRepository.findAll();
        while (notifications.size() < expectedCount && Instant.now().isBefore(deadline)) {
            Thread.sleep(200);
            notifications = notificationRepository.findAll();
        }
        return notifications;
    }
}

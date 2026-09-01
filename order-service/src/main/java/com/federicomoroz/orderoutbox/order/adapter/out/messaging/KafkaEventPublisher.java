package com.federicomoroz.orderoutbox.order.adapter.out.messaging;

import com.federicomoroz.orderoutbox.order.application.port.out.EventPublisherPort;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Blocking on purpose: {@code KafkaTemplate.send(...).get(timeoutMs)} is called synchronously
 * because the outbox relay is not on the HTTP hot path — there is no request thread waiting on
 * this call, so trading a little throughput for simplicity (know immediately whether the publish
 * succeeded) is the right call here.
 *
 * <h2>The subtle part: two deadlines, one verdict</h2>
 *
 * <p>{@code .get(timeout)} is a <em>local</em> deadline — how long <em>this</em> thread waits for
 * an answer. It has no authority over the Kafka client, which keeps retrying the {@code send()}
 * underneath according to its own {@code delivery.timeout.ms}. Two independent deadlines over the
 * same operation is a real hazard, and this repo shipped the bug before fixing it here: with the
 * local wait at 5s and {@code delivery.timeout.ms} left at its 120s default, the relay declared
 * failure and bumped {@code publish_attempts} while the producer went on to actually deliver the
 * record a minute later. The outbox then said {@code FAILED} about events that <em>were</em>
 * published, and re-published them on the next poll — harmless downstream thanks to the idempotent
 * consumer, but real duplicate traffic on the topic and a lying status column.
 *
 * <p>The fix is not a better number, it is an ordering: <strong>the producer's own deadline is the
 * single source of truth, and the local wait is only a safety net strictly larger than it.</strong>
 * The producer therefore always renders its verdict first, and a local timeout can only ever mean
 * "the producer itself hung", never "I got impatient". That relationship is not left to two magic
 * numbers that a future edit could desynchronise: the constructor reads the
 * {@code delivery.timeout.ms} actually in effect on the producer factory — not what the YAML is
 * assumed to say — and refuses to start if the safety net is not strictly larger. A misconfigured
 * deployment fails loudly at boot instead of silently resurrecting the original bug.
 *
 * @see com.federicomoroz.orderoutbox.order.domain.RetryBackoffPolicy the domain-side policy that
 *      decides <em>when</em> a failed event is tried again
 */
@Component
public class KafkaEventPublisher implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    /**
     * What {@code delivery.timeout.ms} falls back to when the producer does not configure it —
     * the Kafka client's own default of 120s. Assuming the configured value instead of reading
     * the effective one is exactly how the two deadlines drifted apart in the first place.
     */
    private static final long KAFKA_CLIENT_DEFAULT_DELIVERY_TIMEOUT_MS = 120_000L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long publishTimeoutMs;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${outbox.relay.publish-timeout-ms:10000}") long publishTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeoutMs = publishTimeoutMs;

        long deliveryTimeoutMs = effectiveDeliveryTimeoutMs(kafkaTemplate);
        if (publishTimeoutMs <= deliveryTimeoutMs) {
            throw new IllegalStateException(
                    "outbox.relay.publish-timeout-ms (" + publishTimeoutMs + "ms) must be strictly greater than the "
                            + "producer's delivery.timeout.ms (" + deliveryTimeoutMs + "ms): the local wait is a "
                            + "safety net, not a second retry policy. With it lower, the relay marks events failed "
                            + "while the producer is still delivering them.");
        }
        log.info("Outbox publisher deadlines: delivery.timeout.ms={} (producer verdict), local safety net={}ms, "
                        + "enable.idempotence={}",
                deliveryTimeoutMs, publishTimeoutMs, effectiveProducerProperty(kafkaTemplate,
                        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "unset (client default: true since Kafka 3.0)"));
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(KafkaTopics.ORDER_CREATED_TOPIC, event.aggregateId(), event.payload())
                    .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("interrupted while publishing outbox event " + event.id(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishException("failed to publish outbox event " + event.id(), e);
        }
    }

    /** The {@code delivery.timeout.ms} the producer will really honour, or the client default. */
    private static long effectiveDeliveryTimeoutMs(KafkaTemplate<String, String> kafkaTemplate) {
        Object configured = effectiveProducerProperty(kafkaTemplate, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, null);
        return configured == null
                ? KAFKA_CLIENT_DEFAULT_DELIVERY_TIMEOUT_MS
                : Long.parseLong(String.valueOf(configured).trim());
    }

    /**
     * Reads one producer property as actually configured on the factory. A {@code ProducerFactory}
     * implementation is allowed not to expose its configuration at all, in which case the caller's
     * fallback applies.
     */
    private static Object effectiveProducerProperty(KafkaTemplate<String, String> kafkaTemplate, String key,
                                                     Object fallback) {
        try {
            Map<String, Object> properties = kafkaTemplate.getProducerFactory().getConfigurationProperties();
            return properties.getOrDefault(key, fallback);
        } catch (UnsupportedOperationException factoryDoesNotExposeItsConfig) {
            return fallback;
        }
    }
}

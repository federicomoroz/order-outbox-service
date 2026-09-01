package com.federicomoroz.orderoutbox.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Wires a dead-letter recoverer for poison pills — a malformed payload that would otherwise fail
 * forever and block its partition. Spring Boot auto-detects this single
 * {@code DefaultErrorHandler} bean and wires it into the listener container factory.
 *
 * <p>After {@link #MAX_RETRIES} local retries, {@code DefaultErrorHandler} hands the failing
 * record to {@link DeadLetterPublishingRecoverer}, which republishes it to
 * {@code order.created.v1.DLT} and lets the container commit the offset and move on.
 */
@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}

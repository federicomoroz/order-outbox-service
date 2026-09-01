package com.federicomoroz.orderoutbox.notification.config;

import com.federicomoroz.orderoutbox.notification.application.port.in.HandleOrderCreatedEventUseCase;
import com.federicomoroz.orderoutbox.notification.application.port.out.NotificationRepository;
import com.federicomoroz.orderoutbox.notification.application.port.out.ProcessedEventRepository;
import com.federicomoroz.orderoutbox.notification.application.port.out.TransactionRunner;
import com.federicomoroz.orderoutbox.notification.application.service.HandleOrderCreatedEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root: the one place in this module where the framework-free
 * {@code application/service} class is instantiated and wired to its Spring-managed adapters.
 */
@Configuration
public class NotificationServiceBeanConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public HandleOrderCreatedEventUseCase handleOrderCreatedEventUseCase(
            ProcessedEventRepository processedEventRepository, NotificationRepository notificationRepository,
            TransactionRunner transactionRunner, Clock clock) {
        return new HandleOrderCreatedEventService(processedEventRepository, notificationRepository,
                transactionRunner, clock);
    }
}

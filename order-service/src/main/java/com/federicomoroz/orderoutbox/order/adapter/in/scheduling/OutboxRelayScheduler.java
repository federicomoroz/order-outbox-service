package com.federicomoroz.orderoutbox.order.adapter.in.scheduling;

import com.federicomoroz.orderoutbox.order.application.port.in.RelayOutboxEventsUseCase;
import com.federicomoroz.orderoutbox.order.application.port.in.RelayOutboxEventsUseCase.RelayOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final RelayOutboxEventsUseCase relayOutboxEventsUseCase;

    public OutboxRelayScheduler(RelayOutboxEventsUseCase relayOutboxEventsUseCase) {
        this.relayOutboxEventsUseCase = relayOutboxEventsUseCase;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:2000}")
    public void relay() {
        RelayOutcome outcome = relayOutboxEventsUseCase.relayDueEvents();
        if (outcome.publishedCount() > 0 || outcome.failedCount() > 0 || outcome.retriedCount() > 0) {
            log.info("Outbox relay run: published={}, retried={}, failed={}",
                    outcome.publishedCount(), outcome.retriedCount(), outcome.failedCount());
        }
    }
}

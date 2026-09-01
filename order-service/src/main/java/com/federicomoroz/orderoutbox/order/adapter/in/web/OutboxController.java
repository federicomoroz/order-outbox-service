package com.federicomoroz.orderoutbox.order.adapter.in.web;

import com.federicomoroz.orderoutbox.order.application.port.out.OutboxQueryPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Observability endpoint over the outbox table — the one view that makes the Transactional
 * Outbox guarantee visible from outside the database: rows appear {@code PENDING} the instant
 * the HTTP request commits, and flip to {@code PUBLISHED} only once the relay actually got an
 * ack from Kafka.
 *
 * <p>Reads straight through the {@link OutboxQueryPort} outbound port instead of going via a
 * {@code port/in} use case — the same deliberate CQRS-lite shortcut already established by
 * {@code NotificationController} in the other module: a plain read with no business logic,
 * where a use-case interface would be ceremony without payoff. The write path
 * ({@code CreateOrderUseCase}, {@code RelayOutboxEventsUseCase}) keeps its inbound ports.
 */
@RestController
@RequestMapping("/api/outbox")
public class OutboxController {

    /** Bounded on purpose: this feeds a dashboard polling once per second, not a data export. */
    static final int RECENT_OUTBOX_EVENTS_LIMIT = 50;

    private final OutboxQueryPort outboxQueryPort;

    public OutboxController(OutboxQueryPort outboxQueryPort) {
        this.outboxQueryPort = outboxQueryPort;
    }

    @GetMapping
    public List<OutboxEventResponse> listRecentOutboxEvents() {
        return outboxQueryPort.findRecent(RECENT_OUTBOX_EVENTS_LIMIT)
                .stream()
                .map(OutboxEventResponse::from)
                .toList();
    }
}

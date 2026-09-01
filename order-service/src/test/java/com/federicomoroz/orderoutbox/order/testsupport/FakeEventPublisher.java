package com.federicomoroz.orderoutbox.order.testsupport;

import com.federicomoroz.orderoutbox.order.application.port.out.EventPublisherPort;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Hand-written fake, configurable to simulate publish failures for specific aggregates so tests
 * can exercise {@code OutboxRelayService}'s retry and per-event isolation behavior. */
public final class FakeEventPublisher implements EventPublisherPort {

    private final List<OutboxEvent> published = new ArrayList<>();
    private final Set<String> aggregateIdsToFail = new HashSet<>();

    @Override
    public void publish(OutboxEvent event) {
        if (aggregateIdsToFail.contains(event.aggregateId())) {
            throw new RuntimeException("simulated publish failure for aggregateId=" + event.aggregateId());
        }
        published.add(event);
    }

    public void failFor(String aggregateId) {
        aggregateIdsToFail.add(aggregateId);
    }

    public void stopFailing(String aggregateId) {
        aggregateIdsToFail.remove(aggregateId);
    }

    public List<OutboxEvent> published() {
        return List.copyOf(published);
    }
}

package com.federicomoroz.orderoutbox.order.testsupport;

import com.federicomoroz.orderoutbox.order.application.port.out.EventSerializer;
import com.federicomoroz.orderoutbox.order.domain.event.OrderCreatedEvent;

/** Hand-written fake: a deterministic, non-JSON "serialization" good enough for unit tests that
 * only need to prove the payload is passed through unchanged, not real JSON shape. */
public final class FakeEventSerializer implements EventSerializer {

    @Override
    public String serialize(OrderCreatedEvent event) {
        return "orderId=%s;customerId=%s;productId=%s;quantity=%d;amount=%s;currency=%s;occurredAt=%s".formatted(
                event.orderId(), event.customerId(), event.productId(), event.quantity(),
                event.unitPriceAmount(), event.unitPriceCurrency(), event.occurredAt());
    }
}

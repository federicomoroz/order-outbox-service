package com.federicomoroz.orderoutbox.notification.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input for {@link HandleOrderCreatedEventUseCase} — the Kafka adapter's own translation of the
 * wire payload into a plain command, so {@code application/} never sees Jackson types. */
public record HandleOrderCreatedEventCommand(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        String productId,
        int quantity,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency,
        Instant occurredAt
) {

    public HandleOrderCreatedEventCommand {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(unitPriceAmount, "unitPriceAmount must not be null");
        Objects.requireNonNull(unitPriceCurrency, "unitPriceCurrency must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}

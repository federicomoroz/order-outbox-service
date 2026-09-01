package com.federicomoroz.orderoutbox.order.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Input for {@link CreateOrderUseCase}. A plain request DTO, not a domain object. */
public record CreateOrderCommand(
        UUID customerId,
        String productId,
        int quantity,
        BigDecimal unitPriceAmount,
        String unitPriceCurrency
) {

    public CreateOrderCommand {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(unitPriceAmount, "unitPriceAmount must not be null");
        Objects.requireNonNull(unitPriceCurrency, "unitPriceCurrency must not be null");
    }
}

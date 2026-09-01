package com.federicomoroz.orderoutbox.order.adapter.in.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank String productId,
        @Positive int quantity,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal unitPriceAmount,
        @NotBlank String unitPriceCurrency
) {
}

package com.federicomoroz.orderoutbox.order.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Monetary amount. Uses {@link BigDecimal}, never {@code double} or {@code float} — the same
 * rule applied to money throughout the portfolio (see {@code shipping-quote}'s use of
 * {@code Decimal}), because floating point binary representation loses cents silently.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative, got " + amount);
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }
}

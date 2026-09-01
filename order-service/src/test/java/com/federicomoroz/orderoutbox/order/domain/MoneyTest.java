package com.federicomoroz.orderoutbox.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void createsMoneyFromAmountAndCurrencyCode() {
        Money money = Money.of(new BigDecimal("19.99"), "USD");

        assertThat(money.amount()).isEqualByComparingTo("19.99");
        assertThat(money.currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01"), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void allowsZeroAmount() {
        Money money = Money.of(BigDecimal.ZERO, "USD");

        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, java.util.Currency.getInstance("USD")))
                .isInstanceOf(NullPointerException.class);
    }
}

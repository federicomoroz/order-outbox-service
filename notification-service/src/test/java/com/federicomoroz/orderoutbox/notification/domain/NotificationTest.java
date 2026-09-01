package com.federicomoroz.orderoutbox.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildsAHumanReadableMessageFromTheOrderFacts() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Notification notification = Notification.forOrderCreated(orderId, customerId, "sku-1", 3, FIXED_CLOCK);

        assertThat(notification.orderId()).isEqualTo(orderId);
        assertThat(notification.customerId()).isEqualTo(customerId);
        assertThat(notification.message()).contains("3").contains("sku-1");
        assertThat(notification.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(notification.id()).isNotNull();
    }

    @Test
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> new Notification(NotificationId.newId(), UUID.randomUUID(), UUID.randomUUID(),
                " ", Instant.now(FIXED_CLOCK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }
}

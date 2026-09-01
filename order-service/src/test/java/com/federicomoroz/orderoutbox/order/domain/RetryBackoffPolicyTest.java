package com.federicomoroz.orderoutbox.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The backoff is pure arithmetic with no clock and no I/O, so it can be pinned down exactly —
 * including the two properties that actually matter operationally: it grows (the relay stops
 * hammering a broker that is down) and it stops growing (a degraded event keeps a slow heartbeat
 * instead of drifting weeks into the future).
 */
class RetryBackoffPolicyTest {

    private static final Instant FAILED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void firstFailureWaitsTheBaseDelay() {
        assertThat(RetryBackoffPolicy.delayAfterAttempt(1)).isEqualTo(RetryBackoffPolicy.BASE_DELAY);
    }

    @Test
    void eachFurtherFailureMultipliesThePreviousDelay() {
        Duration first = RetryBackoffPolicy.delayAfterAttempt(1);
        Duration second = RetryBackoffPolicy.delayAfterAttempt(2);
        Duration third = RetryBackoffPolicy.delayAfterAttempt(3);

        assertThat(second).isEqualTo(first.multipliedBy(RetryBackoffPolicy.MULTIPLIER));
        assertThat(third).isEqualTo(second.multipliedBy(RetryBackoffPolicy.MULTIPLIER));
    }

    @Test
    void delayGrowsStrictlyUntilItReachesTheCap() {
        Duration previous = Duration.ZERO;
        for (int attempt = 1; attempt <= 8; attempt++) {
            Duration current = RetryBackoffPolicy.delayAfterAttempt(attempt);
            assertThat(current).isGreaterThan(previous);
            assertThat(current).isLessThanOrEqualTo(RetryBackoffPolicy.MAX_DELAY);
            previous = current;
        }
    }

    @Test
    void delayIsCappedAndNeverOverflowsNoMatterHowLongTheRowHasBeenFailing() {
        // A row failing for months would blow past Long arithmetic with a naive pow().
        assertThat(RetryBackoffPolicy.delayAfterAttempt(50)).isEqualTo(RetryBackoffPolicy.MAX_DELAY);
        assertThat(RetryBackoffPolicy.delayAfterAttempt(100_000)).isEqualTo(RetryBackoffPolicy.MAX_DELAY);
        assertThat(RetryBackoffPolicy.delayAfterAttempt(Integer.MAX_VALUE))
                .isEqualTo(RetryBackoffPolicy.MAX_DELAY);
    }

    @Test
    void theCapIsMeasuredInMinutes_notHoursOrDays() {
        // Guards the operational intent: a degraded event has to keep retrying often enough to
        // recover on its own within a coffee break, not next week.
        assertThat(RetryBackoffPolicy.MAX_DELAY).isLessThanOrEqualTo(Duration.ofMinutes(10));
        assertThat(RetryBackoffPolicy.MAX_DELAY).isGreaterThan(RetryBackoffPolicy.BASE_DELAY);
    }

    @Test
    void nextAttemptIsAlwaysInTheFuture_evenWellPastMaxPublishAttempts() {
        int wellPastFailure = OutboxEvent.MAX_PUBLISH_ATTEMPTS * 20;

        Instant next = RetryBackoffPolicy.nextAttemptAfter(FAILED_AT, wellPastFailure);

        assertThat(next).isAfter(FAILED_AT);
        assertThat(next).isEqualTo(FAILED_AT.plus(RetryBackoffPolicy.MAX_DELAY));
    }

    @Test
    void nextAttemptOffsetsTheFailureInstantByThatAttemptsDelay() {
        assertThat(RetryBackoffPolicy.nextAttemptAfter(FAILED_AT, 3))
                .isEqualTo(FAILED_AT.plus(RetryBackoffPolicy.delayAfterAttempt(3)));
    }

    @Test
    void rejectsAnAttemptCountThatCannotHaveHappened() {
        assertThatThrownBy(() -> RetryBackoffPolicy.delayAfterAttempt(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetryBackoffPolicy.delayAfterAttempt(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

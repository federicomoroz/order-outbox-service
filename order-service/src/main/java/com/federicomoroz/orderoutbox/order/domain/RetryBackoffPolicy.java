package com.federicomoroz.orderoutbox.order.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * When a failed {@link OutboxEvent} may be published again. Pure domain arithmetic: no clock, no
 * scheduler, no Spring — it is handed the instant a publish failed and answers with the instant
 * the relay is allowed to try that event once more.
 *
 * <p>Exponential, and <strong>capped</strong>. Uncapped exponential backoff eventually pushes the
 * next attempt weeks away, which turns a transient broker outage into permanent data staleness;
 * capping at {@link #MAX_DELAY} means a degraded event settles into a slow, indefinite heartbeat
 * instead. That is what lets {@link OutboxStatus#FAILED} be a <em>soft</em> state — "degraded,
 * still retrying, look at me if I am old" — rather than an abandoned row needing a human.
 *
 * <p>The point of the backoff is to stop the relay hammering a broker that is down once every
 * {@code outbox.relay.poll-interval-ms}: attempts spread out as failures accumulate, so a long
 * outage costs a handful of attempts instead of thousands.
 */
public final class RetryBackoffPolicy {

    /** Delay applied after the first failed attempt, and the base every later delay grows from. */
    static final Duration BASE_DELAY = Duration.ofSeconds(2);

    /** Each additional consecutive failure multiplies the previous delay by this factor. */
    static final int MULTIPLIER = 2;

    /** Ceiling on the delay. Reached after enough failures, and never exceeded afterwards. */
    static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private RetryBackoffPolicy() {
    }

    /**
     * Delay to wait after the {@code publishAttempts}-th consecutive failure:
     * {@link #BASE_DELAY} × {@link #MULTIPLIER}<sup>attempts-1</sup>, clamped to {@link #MAX_DELAY}.
     *
     * <p>Computed by repeated multiplication with an early exit at the cap rather than by
     * {@code pow()}, so a row that has been failing for days cannot overflow the arithmetic.
     */
    public static Duration delayAfterAttempt(int publishAttempts) {
        if (publishAttempts < 1) {
            throw new IllegalArgumentException("publishAttempts must be at least 1, was " + publishAttempts);
        }
        Duration delay = BASE_DELAY;
        for (int attempt = 1; attempt < publishAttempts && delay.compareTo(MAX_DELAY) < 0; attempt++) {
            delay = delay.multipliedBy(MULTIPLIER);
        }
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    /** The earliest instant the relay may retry an event that just failed its {@code publishAttempts}-th time. */
    public static Instant nextAttemptAfter(Instant failedAt, int publishAttempts) {
        return failedAt.plus(delayAfterAttempt(publishAttempts));
    }
}

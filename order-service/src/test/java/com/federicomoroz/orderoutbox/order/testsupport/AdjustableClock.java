package com.federicomoroz.orderoutbox.order.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Hand-written {@link Clock} that tests move forward explicitly. {@code Clock.fixed} is no longer
 * enough for the relay: with exponential backoff, "is this event due yet?" is a question about
 * elapsed time, so a test that never advances its clock can only ever observe the first attempt.
 *
 * <p>Advancing time by hand instead of sleeping keeps these tests instant and deterministic — a
 * backoff of five minutes is exercised in microseconds.
 */
public final class AdjustableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public AdjustableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private AdjustableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** Moves this clock forward. Never backwards: nothing under test would be honest about that. */
    public void advanceBy(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("a clock only moves forward, was asked for " + amount);
        }
        this.instant = instant.plus(amount);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new AdjustableClock(instant, otherZone);
    }
}

package com.sharvary.billing.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that starts wherever it is told and only moves when asked. Used by the demo profile so a
 * visitor can advance billing by a month in one click, and by tests that want to step through
 * several periods.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Clock initial) {
        this(initial.instant(), ZoneOffset.UTC);
    }

    public MutableClock(Instant start, ZoneId zone) {
        this.now = new AtomicReference<>(start);
        this.zone = zone;
    }

    public void advance(Duration by) {
        now.updateAndGet(t -> t.plus(by));
    }

    public void set(Instant to) {
        now.set(to);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock copy = new MutableClock(now.get(), zone);
        return copy;
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}

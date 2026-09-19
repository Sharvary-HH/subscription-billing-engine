package com.sharvary.billing.subscription;

import com.sharvary.billing.plan.BillingInterval;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** A subscription's periods tile time: no gaps, no overlaps, and the anchor never drifts. */
class BillingPeriodsProperties {

    @Property(tries = 1000)
    void consecutivePeriodsNeverSkipOrRepeatADay(@ForAll("starts") LocalDate start, @ForAll("intervals") BillingInterval interval) {
        int anchor = start.getDayOfMonth();
        LocalDate periodStart = start;
        LocalDate previousEnd = null;
        for (int i = 0; i < 36; i++) {
            LocalDate end = BillingPeriods.periodEnd(periodStart, anchor, interval);
            assertThat(end).isAfter(periodStart);
            if (previousEnd != null) {
                assertThat(periodStart).isEqualTo(previousEnd);
            }
            // The anchor is honoured whenever the month is long enough for it.
            int expectedDay = Math.min(anchor, YearMonth.from(end).lengthOfMonth());
            assertThat(end.getDayOfMonth()).isEqualTo(expectedDay);
            previousEnd = end;
            periodStart = end;
        }
    }

    @Property(tries = 500)
    void monthlyPeriodsAreBetween28And31Days(@ForAll("starts") LocalDate start) {
        int anchor = start.getDayOfMonth();
        LocalDate periodStart = start;
        for (int i = 0; i < 24; i++) {
            LocalDate end = BillingPeriods.periodEnd(periodStart, anchor, BillingInterval.MONTHLY);
            long days = java.time.temporal.ChronoUnit.DAYS.between(periodStart, end);
            assertThat(days).isBetween(28L, 31L);
            periodStart = end;
        }
    }

    @Provide
    Arbitrary<LocalDate> starts() {
        return Arbitraries.integers().between(0, 4000).map(d -> LocalDate.of(2020, 1, 1).plusDays(d));
    }

    @Provide
    Arbitrary<BillingInterval> intervals() {
        return Arbitraries.of(BillingInterval.class);
    }
}

package com.sharvary.billing.subscription.proration;

import com.sharvary.billing.common.Money;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Daily proration as a pure function. No clock, no database, nothing to mock.
 *
 * <p>The amount owed for a span of days inside a period is defined through a cumulative function:
 * {@code owed(day d) = floor(price * d / daysInPeriod)}, and the charge for {@code [from, to)} is
 * {@code owed(to) - owed(from)}. That gives three properties that per-span rounding does not:
 *
 * <ul>
 *   <li>the whole period costs exactly the full price ({@code owed(total) == price});</li>
 *   <li>any partition of the period into spans sums exactly to the full price, so a customer who
 *       upgrades, downgrades and upgrades again in one cycle is charged precisely the sum of each
 *       plan's price for the days they were on it; the rounding telescopes away;</li>
 *   <li>no individual day is ever worth more than one minor unit more than any other.</li>
 * </ul>
 *
 * Granularity is the calendar day. A change made at 09:00 or 23:59 counts from the same date.
 * See docs/adr/0002-daily-proration.md.
 */
public final class ProrationCalculator {

    private ProrationCalculator() {
    }

    /** What a customer is credited and charged when they move from one price to another mid-period. */
    public record Result(Money credit, Money charge, long daysUsed, long daysRemaining, long daysInPeriod) {

        public Money net() {
            return charge.minus(credit);
        }
    }

    /**
     * @param oldPrice    full-period price of what they were on
     * @param newPrice    full-period price of what they are moving to
     * @param periodStart inclusive
     * @param periodEnd   exclusive
     * @param changeDate  the day the change takes effect; must lie inside the period
     */
    public static Result planChange(Money oldPrice, Money newPrice, LocalDate periodStart, LocalDate periodEnd,
                                    LocalDate changeDate) {
        requireInside(periodStart, periodEnd, changeDate);
        long total = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long used = ChronoUnit.DAYS.between(periodStart, changeDate);
        Money credit = amountFor(oldPrice, periodStart, periodEnd, changeDate, periodEnd);
        Money charge = amountFor(newPrice, periodStart, periodEnd, changeDate, periodEnd);
        return new Result(credit, charge, used, total - used, total);
    }

    /** The price of {@code [from, to)} within {@code [periodStart, periodEnd)} at a given full-period price. */
    public static Money amountFor(Money fullPrice, LocalDate periodStart, LocalDate periodEnd,
                                  LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from " + from + " is after to " + to);
        }
        requireInside(periodStart, periodEnd, from);
        if (to.isAfter(periodEnd)) {
            throw new IllegalArgumentException(to + " is outside the period ending " + periodEnd);
        }
        long total = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long fromDay = ChronoUnit.DAYS.between(periodStart, from);
        long toDay = ChronoUnit.DAYS.between(periodStart, to);
        long minor = owedThrough(fullPrice.minor(), toDay, total) - owedThrough(fullPrice.minor(), fromDay, total);
        return Money.of(minor, fullPrice.currency());
    }

    /** floor(price * day / total), computed without overflow. Monotone in {@code day}. */
    static long owedThrough(long priceMinor, long day, long totalDays) {
        if (totalDays <= 0) {
            throw new IllegalArgumentException("period must contain at least one day");
        }
        return BigInteger.valueOf(priceMinor)
                .multiply(BigInteger.valueOf(day))
                .divide(BigInteger.valueOf(totalDays))
                .longValueExact();
    }

    private static void requireInside(LocalDate periodStart, LocalDate periodEnd, LocalDate date) {
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("period end must be after start");
        }
        if (date.isBefore(periodStart) || date.isAfter(periodEnd)) {
            throw new IllegalArgumentException(date + " is outside the period " + periodStart + ".." + periodEnd);
        }
    }
}

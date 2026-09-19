package com.sharvary.billing.subscription;

import com.sharvary.billing.plan.BillingInterval;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Billing period arithmetic. Periods are half-open: {@code [start, end)}.
 *
 * <p>The anchor day is the day of month the customer originally subscribed on, and it is stored
 * separately from the period dates so it cannot drift. A subscription anchored on the 31st bills
 * on 31 Jan, 28 Feb, 31 Mar, 30 Apr, 31 May: each period end is computed from the anchor and the
 * target month, never from the previous (possibly clamped) date.
 */
public final class BillingPeriods {

    private BillingPeriods() {
    }

    /** The end of the period that starts on {@code start}, i.e. the next billing date. */
    public static LocalDate periodEnd(LocalDate start, int anchorDay, BillingInterval interval) {
        if (anchorDay < 1 || anchorDay > 31) {
            throw new IllegalArgumentException("anchor day must be 1..31, got " + anchorDay);
        }
        YearMonth target = YearMonth.from(start).plusMonths(interval.months());
        LocalDate end = onAnchor(target, anchorDay);
        // Guard for a start that sits after the anchor in its own month (a mid-month resume, say):
        // the next anchor date must be strictly after the start.
        while (!end.isAfter(start)) {
            target = target.plusMonths(1);
            end = onAnchor(target, anchorDay);
        }
        return end;
    }

    /** The anchor day in a given month, clamped to the month's length. */
    public static LocalDate onAnchor(YearMonth month, int anchorDay) {
        return month.atDay(Math.min(anchorDay, month.lengthOfMonth()));
    }
}

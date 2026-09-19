package com.sharvary.billing.subscription;

import com.sharvary.billing.plan.BillingInterval;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingPeriodsTest {

    @Test
    void thirtyFirstAnchorClampsInShortMonthsAndComesBack() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        LocalDate feb = BillingPeriods.periodEnd(start, 31, BillingInterval.MONTHLY);
        LocalDate mar = BillingPeriods.periodEnd(feb, 31, BillingInterval.MONTHLY);
        LocalDate apr = BillingPeriods.periodEnd(mar, 31, BillingInterval.MONTHLY);
        LocalDate may = BillingPeriods.periodEnd(apr, 31, BillingInterval.MONTHLY);
        assertThat(feb).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(mar).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(apr).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(may).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void leapYearFebruary() {
        assertThat(BillingPeriods.periodEnd(LocalDate.of(2028, 1, 30), 30, BillingInterval.MONTHLY))
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void annualOnLeapDay() {
        LocalDate start = LocalDate.of(2024, 2, 29);
        LocalDate y1 = BillingPeriods.periodEnd(start, 29, BillingInterval.ANNUAL);
        LocalDate y2 = BillingPeriods.periodEnd(y1, 29, BillingInterval.ANNUAL);
        assertThat(y1).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(y2).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void midMonthStartAfterAnchorRollsToTheNextAnchorAfterIt() {
        // Resumed on the 20th with an anchor of the 5th: the next 5th is next month, not the month after.
        assertThat(BillingPeriods.periodEnd(LocalDate.of(2026, 3, 20), 5, BillingInterval.MONTHLY))
                .isEqualTo(LocalDate.of(2026, 4, 5));
        // Started on the 2nd with anchor 5th (odd, but must still be strictly after the start).
        assertThat(BillingPeriods.periodEnd(LocalDate.of(2026, 3, 2), 5, BillingInterval.MONTHLY))
                .isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    void onAnchorClamps() {
        assertThat(BillingPeriods.onAnchor(YearMonth.of(2026, 2), 31)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BillingPeriods.onAnchor(YearMonth.of(2026, 2), 10)).isEqualTo(LocalDate.of(2026, 2, 10));
    }

    @Test
    void rejectsBadAnchor() {
        assertThatThrownBy(() -> BillingPeriods.periodEnd(LocalDate.of(2026, 1, 1), 0, BillingInterval.MONTHLY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BillingPeriods.periodEnd(LocalDate.of(2026, 1, 1), 32, BillingInterval.MONTHLY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

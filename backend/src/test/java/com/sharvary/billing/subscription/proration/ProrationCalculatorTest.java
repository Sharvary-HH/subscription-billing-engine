package com.sharvary.billing.subscription.proration;

import com.sharvary.billing.common.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProrationCalculatorTest {

    static final LocalDate MAR_1 = LocalDate.of(2026, 3, 1);
    static final LocalDate APR_1 = LocalDate.of(2026, 4, 1);

    static Money usd(long minor) {
        return Money.of(minor, "USD");
    }

    @Test
    void halfwayThroughAThirtyOneDayMonth() {
        // 31 days, change on day 15 (14 used, 17 remaining). 3100 cents -> exactly 100/day.
        var r = ProrationCalculator.planChange(usd(3100), usd(6200), MAR_1, APR_1, LocalDate.of(2026, 3, 15));
        assertThat(r.daysInPeriod()).isEqualTo(31);
        assertThat(r.daysUsed()).isEqualTo(14);
        assertThat(r.daysRemaining()).isEqualTo(17);
        assertThat(r.credit()).isEqualTo(usd(1700));
        assertThat(r.charge()).isEqualTo(usd(3400));
        assertThat(r.net()).isEqualTo(usd(1700));
    }

    @Test
    void changeOnTheFirstDayCreditsEverything() {
        var r = ProrationCalculator.planChange(usd(1999), usd(4999), MAR_1, APR_1, MAR_1);
        assertThat(r.credit()).isEqualTo(usd(1999));
        assertThat(r.charge()).isEqualTo(usd(4999));
    }

    @Test
    void changeOnTheLastDayCreditsOneDay() {
        var r = ProrationCalculator.planChange(usd(3100), usd(6200), MAR_1, APR_1, LocalDate.of(2026, 3, 31));
        assertThat(r.credit()).isEqualTo(usd(100));
        assertThat(r.charge()).isEqualTo(usd(200));
    }

    @Test
    void changeAtPeriodEndIsZero() {
        var r = ProrationCalculator.planChange(usd(3100), usd(6200), MAR_1, APR_1, APR_1);
        assertThat(r.credit()).isEqualTo(usd(0));
        assertThat(r.charge()).isEqualTo(usd(0));
    }

    @Test
    void downgradeNetsToACredit() {
        var r = ProrationCalculator.planChange(usd(9900), usd(1900), MAR_1, APR_1, LocalDate.of(2026, 3, 11));
        assertThat(r.net().isNegative()).isTrue();
        assertThat(r.credit().compareTo(r.charge())).isGreaterThan(0);
    }

    @Test
    void usedPlusUnusedIsExactlyTheFullPrice() {
        Money price = usd(1999); // 31 days, does not divide evenly
        for (int day = 0; day <= 31; day++) {
            LocalDate change = MAR_1.plusDays(day);
            Money used = ProrationCalculator.amountFor(price, MAR_1, APR_1, MAR_1, change);
            Money unused = ProrationCalculator.amountFor(price, MAR_1, APR_1, change, APR_1);
            assertThat(used.plus(unused)).as("day %d", day).isEqualTo(price);
        }
    }

    @Test
    void threeChangesInOneCycleTelescope() {
        // On A for days 0-9, B for 10-19, C for 20-24, back to A for 25-31.
        Money a = usd(1999);
        Money b = usd(4999);
        Money c = usd(999);
        LocalDate d1 = MAR_1.plusDays(10);
        LocalDate d2 = MAR_1.plusDays(20);
        LocalDate d3 = MAR_1.plusDays(25);

        Money charged = a; // invoice at start of period
        var first = ProrationCalculator.planChange(a, b, MAR_1, APR_1, d1);
        charged = charged.plus(first.net());
        var second = ProrationCalculator.planChange(b, c, MAR_1, APR_1, d2);
        charged = charged.plus(second.net());
        var third = ProrationCalculator.planChange(c, a, MAR_1, APR_1, d3);
        charged = charged.plus(third.net());

        Money expected = ProrationCalculator.amountFor(a, MAR_1, APR_1, MAR_1, d1)
                .plus(ProrationCalculator.amountFor(b, MAR_1, APR_1, d1, d2))
                .plus(ProrationCalculator.amountFor(c, MAR_1, APR_1, d2, d3))
                .plus(ProrationCalculator.amountFor(a, MAR_1, APR_1, d3, APR_1));
        assertThat(charged).isEqualTo(expected);
    }

    @Test
    void rejectsDatesOutsideThePeriod() {
        assertThatThrownBy(() -> ProrationCalculator.planChange(usd(1), usd(2), MAR_1, APR_1, LocalDate.of(2026, 2, 28)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProrationCalculator.planChange(usd(1), usd(2), MAR_1, APR_1, LocalDate.of(2026, 4, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProrationCalculator.planChange(usd(1), usd(2), APR_1, MAR_1, MAR_1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProrationCalculator.amountFor(usd(1), MAR_1, APR_1, MAR_1.plusDays(5), MAR_1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProrationCalculator.amountFor(usd(1), MAR_1, APR_1, MAR_1, APR_1.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProrationCalculator.owedThrough(100, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

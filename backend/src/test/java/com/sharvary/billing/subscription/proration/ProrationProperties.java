package com.sharvary.billing.subscription.proration;

import com.sharvary.billing.common.Money;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProrationProperties {

    record Scenario(Money oldPrice, Money newPrice, LocalDate start, LocalDate end, LocalDate change) {
    }

    @Property(tries = 2000)
    void creditNeverExceedsOldPriceAndChargeNeverExceedsNewPrice(@ForAll("scenarios") Scenario s) {
        var r = ProrationCalculator.planChange(s.oldPrice(), s.newPrice(), s.start(), s.end(), s.change());
        assertThat(r.credit().minor()).isBetween(0L, s.oldPrice().minor());
        assertThat(r.charge().minor()).isBetween(0L, s.newPrice().minor());
    }

    @Property(tries = 2000)
    void usedPlusUnusedIsTheFullPrice(@ForAll("scenarios") Scenario s) {
        Money used = ProrationCalculator.amountFor(s.oldPrice(), s.start(), s.end(), s.start(), s.change());
        Money unused = ProrationCalculator.amountFor(s.oldPrice(), s.start(), s.end(), s.change(), s.end());
        assertThat(used.plus(unused)).isEqualTo(s.oldPrice());
    }

    @Property(tries = 1000)
    void anyPartitionOfThePeriodSumsToTheFullPrice(@ForAll("scenarios") Scenario s, @ForAll("cuts") List<Integer> cuts) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(s.start(), s.end());
        List<LocalDate> points = new java.util.ArrayList<>();
        points.add(s.start());
        cuts.stream().map(c -> s.start().plusDays(c % (days + 1))).sorted().distinct().forEach(points::add);
        points.add(s.end());
        Money sum = Money.zero(s.oldPrice().currency());
        for (int i = 0; i + 1 < points.size(); i++) {
            LocalDate from = points.get(i);
            LocalDate to = points.get(i + 1);
            if (from.isAfter(to)) {
                continue;
            }
            sum = sum.plus(ProrationCalculator.amountFor(s.oldPrice(), s.start(), s.end(), from, to));
        }
        assertThat(sum).isEqualTo(s.oldPrice());
    }

    @Property(tries = 1000)
    void moreExpensivePlanNeverNetsToACreditWhenUpgrading(@ForAll("scenarios") Scenario s) {
        if (s.newPrice().compareTo(s.oldPrice()) < 0) {
            return;
        }
        var r = ProrationCalculator.planChange(s.oldPrice(), s.newPrice(), s.start(), s.end(), s.change());
        assertThat(r.net().isNegative()).isFalse();
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Long> prices = Arbitraries.longs().between(0, 50_000_00);
        Arbitrary<LocalDate> starts = Arbitraries.integers().between(0, 3000)
                .map(d -> LocalDate.of(2024, 1, 1).plusDays(d));
        Arbitrary<Integer> lengths = Arbitraries.integers().between(1, 366);
        Arbitrary<Double> position = Arbitraries.doubles().between(0.0, 1.0);
        return Combinators.combine(prices, prices, starts, lengths, position).as((oldP, newP, start, len, pos) -> {
            LocalDate end = start.plusDays(len);
            LocalDate change = start.plusDays(Math.round(pos * len));
            return new Scenario(Money.of(oldP, "USD"), Money.of(newP, "USD"), start, end, change);
        });
    }

    @Provide
    Arbitrary<List<Integer>> cuts() {
        return Arbitraries.integers().between(0, 400).list().ofMinSize(0).ofMaxSize(8);
    }
}

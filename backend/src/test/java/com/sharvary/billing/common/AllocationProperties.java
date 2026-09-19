package com.sharvary.billing.common;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one invariant that matters, checked across thousands of generated cases: the parts sum to
 * the whole. Also: no part is ever more than one unit away from its exact share, and the
 * allocation is deterministic.
 */
class AllocationProperties {

    @Property(tries = 2000)
    void partsAlwaysSumToTheTotal(@ForAll("totals") long total, @ForAll("weights") long[] weights) {
        long[] parts = Allocation.largestRemainder(total, weights);
        assertThat(parts).hasSameSizeAs(weights);
        assertThat(Arrays.stream(parts).sum()).isEqualTo(total);
    }

    @Property(tries = 1000)
    void noPartIsMoreThanOneUnitFromItsExactShare(@ForAll("totals") long total, @ForAll("weights") long[] weights) {
        long[] parts = Allocation.largestRemainder(total, weights);
        long weightSum = Arrays.stream(weights).sum();
        for (int i = 0; i < parts.length; i++) {
            double exact = (double) total * weights[i] / weightSum;
            assertThat(Math.abs(parts[i] - exact)).isLessThan(1.0 + 1e-6);
        }
    }

    @Property(tries = 500)
    void allocationIsDeterministic(@ForAll("totals") long total, @ForAll("weights") long[] weights) {
        assertThat(Allocation.largestRemainder(total, weights)).containsExactly(Allocation.largestRemainder(total, weights));
    }

    @Property(tries = 1000)
    void equalSplitsDifferByAtMostOneUnit(@ForAll("totals") long total, @ForAll("partCounts") int parts) {
        Money[] pieces = Money.of(total, "USD").allocate(parts);
        long min = Arrays.stream(pieces).mapToLong(Money::minor).min().orElseThrow();
        long max = Arrays.stream(pieces).mapToLong(Money::minor).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
        assertThat(Arrays.stream(pieces).mapToLong(Money::minor).sum()).isEqualTo(total);
    }

    @Provide
    Arbitrary<Long> totals() {
        return Arbitraries.longs().between(-10_000_000_000L, 10_000_000_000L);
    }

    @Provide
    Arbitrary<long[]> weights() {
        return Arbitraries.longs().between(0, 1_000_000).array(long[].class).ofMinSize(1).ofMaxSize(40)
                .filter(w -> Arrays.stream(w).sum() > 0);
    }

    @Provide
    Arbitrary<Integer> partCounts() {
        return Arbitraries.integers().between(1, 50);
    }
}

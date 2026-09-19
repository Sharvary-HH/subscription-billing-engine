package com.sharvary.billing.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationTest {

    @Test
    void hundredThreeWaysIsNotNinetyNineNinetyNine() {
        assertThat(Allocation.largestRemainder(10000, new long[]{1, 1, 1})).containsExactly(3334, 3333, 3333);
    }

    @Test
    void remainderGoesToLargestFractionsFirst() {
        // 7 in ratio 1:2:4 -> exact 1.0, 2.0, 4.0 -> no remainder
        assertThat(Allocation.largestRemainder(7, new long[]{1, 2, 4})).containsExactly(1, 2, 4);
        // 10 in ratio 1:1:1 -> floor 3,3,3, remainder 1 goes to index 0 (tie broken by index)
        assertThat(Allocation.largestRemainder(10, new long[]{1, 1, 1})).containsExactly(4, 3, 3);
        // 5 in 1:2 -> exact 1.67, 3.33 -> floor 1,3, remainder 1 goes to the .67
        assertThat(Allocation.largestRemainder(5, new long[]{1, 2})).containsExactly(2, 3);
    }

    @Test
    void zeroWeightsGetNothing() {
        assertThat(Allocation.largestRemainder(100, new long[]{0, 1, 0})).containsExactly(0, 100, 0);
    }

    @Test
    void negativeTotalsAllocateSymmetrically() {
        assertThat(Allocation.largestRemainder(-10000, new long[]{1, 1, 1})).containsExactly(-3334, -3333, -3333);
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> Allocation.largestRemainder(1, new long[]{})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocation.largestRemainder(1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocation.largestRemainder(1, new long[]{0, 0})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocation.largestRemainder(1, new long[]{1, -1})).isInstanceOf(IllegalArgumentException.class);
    }
}

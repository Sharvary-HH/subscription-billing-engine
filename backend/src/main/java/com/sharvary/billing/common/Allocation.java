package com.sharvary.billing.common;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Largest-remainder allocation of an integer total across weighted parts.
 *
 * <p>Each part first receives the floor of its exact share. Whatever is left over (always fewer
 * units than there are parts) is handed out one unit at a time to the parts with the largest
 * fractional remainders. Ties go to the earlier index so the result is deterministic.
 *
 * <p>The single invariant: {@code sum(result) == total}, for any total (including negative) and
 * any weights. Naive per-part rounding breaks this; 100.00 three ways gives 33.33 x 3 = 99.99.
 */
public final class Allocation {

    private Allocation() {
    }

    public static long[] largestRemainder(long total, long[] weights) {
        if (weights == null || weights.length == 0) {
            throw new IllegalArgumentException("at least one weight is required");
        }
        long weightSum = 0;
        for (long w : weights) {
            if (w < 0) {
                throw new IllegalArgumentException("weights must be non-negative, got " + w);
            }
            weightSum = Math.addExact(weightSum, w);
        }
        if (weightSum == 0) {
            throw new IllegalArgumentException("weights must not all be zero");
        }

        // Negative totals (credits) are allocated on the absolute value and flipped back, so the
        // remainder logic only ever deals with non-negative numbers.
        boolean negative = total < 0;
        BigInteger abs = BigInteger.valueOf(total).abs();
        BigInteger sum = BigInteger.valueOf(weightSum);

        long[] result = new long[weights.length];
        BigInteger[] remainders = new BigInteger[weights.length];
        long distributed = 0;
        for (int i = 0; i < weights.length; i++) {
            BigInteger[] divRem = abs.multiply(BigInteger.valueOf(weights[i])).divideAndRemainder(sum);
            result[i] = divRem[0].longValueExact();
            remainders[i] = divRem[1];
            distributed += result[i];
        }

        long leftover = abs.longValueExact() - distributed;
        Integer[] order = IntStream.range(0, weights.length).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.<Integer, BigInteger>comparing(i -> remainders[i]).reversed()
                .thenComparingInt(i -> i));
        for (int k = 0; k < leftover; k++) {
            result[order[k]]++;
        }

        if (negative) {
            for (int i = 0; i < result.length; i++) {
                result[i] = -result[i];
            }
        }
        return result;
    }
}

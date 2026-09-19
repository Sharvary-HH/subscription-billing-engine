package com.sharvary.billing.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in a single currency, held as integer minor units (cents, paise, pence).
 *
 * <p>There is deliberately no constructor that takes a double. Every arithmetic operation either
 * stays in the integers or takes an explicit {@link RoundingMode}, so there is never a hidden
 * rounding decision. Two Money values in different currencies cannot be combined; trying throws.
 */
public record Money(long minor, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minor, Currency currency) {
        return new Money(minor, currency);
    }

    public static Money of(long minor, String currencyCode) {
        return new Money(minor, Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    /** Parses a major-unit decimal such as "19.99" using the currency's own scale. */
    public static Money parse(String major, Currency currency) {
        BigDecimal scaled = new BigDecimal(major).setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        return new Money(scaled.movePointRight(currency.getDefaultFractionDigits()).longValueExact(), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minor, other.minor), currency);
    }

    public Money times(long quantity) {
        return new Money(Math.multiplyExact(minor, quantity), currency);
    }

    /** Multiplies by an arbitrary ratio. The caller has to say how the fractional minor unit is resolved. */
    public Money times(BigDecimal factor, RoundingMode rounding) {
        BigDecimal result = BigDecimal.valueOf(minor).multiply(factor).setScale(0, rounding);
        return new Money(result.longValueExact(), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minor), currency);
    }

    public Money abs() {
        return minor < 0 ? negate() : this;
    }

    public boolean isZero() {
        return minor == 0;
    }

    public boolean isNegative() {
        return minor < 0;
    }

    public boolean isPositive() {
        return minor > 0;
    }

    public Money min(Money other) {
        requireSameCurrency(other);
        return minor <= other.minor ? this : other;
    }

    /** Splits this amount into {@code parts} equal-weight pieces that sum exactly to this amount. */
    public Money[] allocate(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive, got " + parts);
        }
        long[] weights = new long[parts];
        Arrays.fill(weights, 1L);
        return allocate(weights);
    }

    /**
     * Splits this amount in proportion to {@code weights} using largest-remainder allocation.
     * The returned pieces always sum exactly to this amount; see {@link Allocation}.
     */
    public Money[] allocate(long[] weights) {
        long[] pieces = Allocation.largestRemainder(minor, weights);
        Money[] result = new Money[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            result[i] = new Money(pieces[i], currency);
        }
        return result;
    }

    /** Major-unit decimal, e.g. 1999 minor USD -> 19.99. For display and serialisation only. */
    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minor).movePointLeft(currency.getDefaultFractionDigits());
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + toMajor().toPlainString();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}

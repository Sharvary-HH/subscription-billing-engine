package com.sharvary.billing.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    static final Currency USD = Currency.getInstance("USD");
    static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void addsAndSubtractsInMinorUnits() {
        Money a = Money.of(1999, USD);
        Money b = Money.of(1, USD);
        assertThat(a.plus(b)).isEqualTo(Money.of(2000, USD));
        assertThat(a.minus(b)).isEqualTo(Money.of(1998, USD));
        assertThat(a.times(3)).isEqualTo(Money.of(5997, USD));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of(1, USD).plus(Money.of(1, EUR)))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("USD").hasMessageContaining("EUR");
        assertThatThrownBy(() -> Money.of(1, USD).minus(Money.of(1, EUR))).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Money.of(1, USD).compareTo(Money.of(1, EUR))).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Money.of(1, USD).min(Money.of(1, EUR))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void multiplyingByARatioRequiresAnExplicitRoundingMode() {
        Money price = Money.of(1000, USD);
        assertThat(price.times(new BigDecimal("0.185"), RoundingMode.HALF_UP)).isEqualTo(Money.of(185, USD));
        assertThat(price.times(new BigDecimal("0.1855"), RoundingMode.HALF_UP)).isEqualTo(Money.of(186, USD));
        assertThat(price.times(new BigDecimal("0.1855"), RoundingMode.DOWN)).isEqualTo(Money.of(185, USD));
    }

    @Test
    void parsesAndFormatsMajorUnits() {
        assertThat(Money.parse("19.99", USD)).isEqualTo(Money.of(1999, USD));
        assertThat(Money.of(1999, USD).toMajor()).isEqualByComparingTo("19.99");
        assertThat(Money.of(1999, USD).toString()).isEqualTo("USD 19.99");
        assertThat(Money.of(500, Currency.getInstance("JPY")).toMajor()).isEqualByComparingTo("500");
        assertThatThrownBy(() -> Money.parse("19.999", USD)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void allocatesIntoEqualPartsThatSumExactly() {
        Money[] parts = Money.of(10000, USD).allocate(3);
        assertThat(parts).containsExactly(Money.of(3334, USD), Money.of(3333, USD), Money.of(3333, USD));
        assertThat(parts[0].plus(parts[1]).plus(parts[2])).isEqualTo(Money.of(10000, USD));
    }

    @Test
    void allocatesByWeights() {
        Money[] parts = Money.of(100, USD).allocate(new long[]{1, 1, 1});
        assertThat(parts).containsExactly(Money.of(34, USD), Money.of(33, USD), Money.of(33, USD));
        assertThatThrownBy(() -> Money.of(100, USD).allocate(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signHelpers() {
        assertThat(Money.of(-5, USD).isNegative()).isTrue();
        assertThat(Money.of(-5, USD).abs()).isEqualTo(Money.of(5, USD));
        assertThat(Money.of(5, USD).negate()).isEqualTo(Money.of(-5, USD));
        assertThat(Money.zero(USD).isZero()).isTrue();
        assertThat(Money.of(5, USD).isPositive()).isTrue();
        assertThat(Money.of(5, USD).min(Money.of(3, USD))).isEqualTo(Money.of(3, USD));
        assertThat(Money.of(5, "USD").currencyCode()).isEqualTo("USD");
    }

    @Test
    void overflowThrowsRatherThanWrapping() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, USD).plus(Money.of(1, USD))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, USD).times(2)).isInstanceOf(ArithmeticException.class);
    }
}

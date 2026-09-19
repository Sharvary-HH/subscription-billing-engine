package com.sharvary.billing.invoice.allocation;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.invoice.LineType;
import com.sharvary.billing.invoice.allocation.InvoiceCalculator.ProposedLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceCalculatorTest {

    static final Currency USD = Currency.getInstance("USD");
    static final LocalDate D = LocalDate.of(2026, 3, 1);

    static ProposedLine line(LineType type, long minor) {
        return new ProposedLine(type, type.name(), 1, Money.of(minor, USD), Money.of(minor, USD), D, D.plusMonths(1), false);
    }

    @Test
    void perLineTaxSumsExactlyToInvoiceTax() {
        // Three lines of 33.33 at 18%: 99.99 * 0.18 = 17.9982 -> 18.00. Naive per-line: 6.00 x 3 = 18.00 here,
        // but at 33.35 x 3 = 100.05 * .18 = 18.009 -> 18.01 vs per-line 6.003 -> 6.00 x 3 = 18.00.
        var r = InvoiceCalculator.compute(List.of(line(LineType.PLAN, 3335), line(LineType.PLAN, 3335), line(LineType.PLAN, 3335)),
                new BigDecimal("0.18"), Money.zero(USD), D);
        assertThat(r.subtotal()).isEqualTo(Money.of(10005, USD));
        assertThat(r.tax()).isEqualTo(Money.of(1801, USD));
        long lineTax = r.lines().stream().mapToLong(l -> l.tax().minor()).sum();
        assertThat(lineTax).isEqualTo(1801);
        assertThat(r.total()).isEqualTo(Money.of(11806, USD));
    }

    @Test
    void creditLinesCarryNoTaxAndReduceTheTaxable() {
        var r = InvoiceCalculator.compute(List.of(line(LineType.PRORATION_CREDIT, -1000), line(LineType.PRORATION_CHARGE, 3000)),
                new BigDecimal("0.20"), Money.zero(USD), D);
        assertThat(r.subtotal()).isEqualTo(Money.of(2000, USD));
        assertThat(r.tax()).isEqualTo(Money.of(400, USD));
        assertThat(r.lines().get(0).tax()).isEqualTo(Money.zero(USD));
        assertThat(r.lines().get(1).tax()).isEqualTo(Money.of(400, USD));
    }

    @Test
    void creditBalanceIsAppliedAfterTaxAndNeverBelowZero() {
        var r = InvoiceCalculator.compute(List.of(line(LineType.PLAN, 1000)), new BigDecimal("0.10"), Money.of(5000, USD), D);
        assertThat(r.creditApplied()).isEqualTo(Money.of(1100, USD));
        assertThat(r.total()).isEqualTo(Money.zero(USD));
        assertThat(r.lines()).hasSize(2);
        assertThat(r.lines().get(1).line().type()).isEqualTo(LineType.CREDIT_BALANCE);
        assertThat(r.lines().get(1).line().amount()).isEqualTo(Money.of(-1100, USD));
    }

    @Test
    void partialCreditBalance() {
        var r = InvoiceCalculator.compute(List.of(line(LineType.PLAN, 1000)), BigDecimal.ZERO, Money.of(300, USD), D);
        assertThat(r.creditApplied()).isEqualTo(Money.of(300, USD));
        assertThat(r.total()).isEqualTo(Money.of(700, USD));
    }

    @Test
    void negativeSubtotalHasNoTaxAndNoCreditDraw() {
        var r = InvoiceCalculator.compute(List.of(line(LineType.PRORATION_CREDIT, -1000)), new BigDecimal("0.20"), Money.of(500, USD), D);
        assertThat(r.tax()).isEqualTo(Money.zero(USD));
        assertThat(r.creditApplied()).isEqualTo(Money.zero(USD));
        assertThat(r.total()).isEqualTo(Money.of(-1000, USD));
        assertThat(r.lines()).hasSize(1);
    }

    @Test
    void needsAtLeastOneLine() {
        assertThatThrownBy(() -> InvoiceCalculator.compute(List.of(), BigDecimal.ZERO, Money.zero(USD), D))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

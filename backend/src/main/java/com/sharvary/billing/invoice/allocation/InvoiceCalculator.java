package com.sharvary.billing.invoice.allocation;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.invoice.LineType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a list of proposed lines into an invoice's numbers. Pure: no clock, no persistence.
 *
 * <p>Tax is computed once on the taxable subtotal (rounded half-up to the minor unit) and then
 * spread back over the taxable lines with largest-remainder allocation, so the per-line tax
 * column always adds up to the invoice's tax figure. Naively taxing each line and summing would
 * drift by a unit or two on a long invoice, and finance teams notice that.
 *
 * <p>Credit balance is applied last, against the taxed total, and never pushes the total below
 * zero. Whatever is not used stays on the customer's balance for the next invoice.
 */
public final class InvoiceCalculator {

    private InvoiceCalculator() {
    }

    public record ProposedLine(LineType type, String description, long quantity, Money unitPrice, Money amount,
                               LocalDate periodStart, LocalDate periodEnd, boolean proration) {
    }

    public record ComputedLine(ProposedLine line, Money tax) {
    }

    public record Result(List<ComputedLine> lines, Money subtotal, Money tax, Money creditApplied, Money total) {
    }

    /**
     * @param taxRate         e.g. 0.20 for 20 percent; applied to the sum of positive lines only
     * @param creditAvailable the customer's carried-forward balance, drawn down as far as needed
     */
    public static Result compute(List<ProposedLine> proposed, BigDecimal taxRate, Money creditAvailable, LocalDate today) {
        if (proposed.isEmpty()) {
            throw new IllegalArgumentException("an invoice needs at least one line");
        }
        var currency = creditAvailable.currency();
        Money subtotal = Money.zero(currency);
        for (ProposedLine line : proposed) {
            subtotal = subtotal.plus(line.amount());
        }

        // Tax on the net of charges and credits, floored at zero. A pure-credit invoice carries no tax.
        Money taxable = subtotal.isNegative() ? Money.zero(currency) : subtotal;
        Money tax = taxable.times(taxRate, RoundingMode.HALF_UP);

        long[] weights = proposed.stream().mapToLong(l -> Math.max(0, l.amount().minor())).toArray();
        boolean anyPositive = false;
        for (long w : weights) {
            anyPositive |= w > 0;
        }
        Money[] taxPerLine = anyPositive ? tax.allocate(weights) : zeros(proposed.size(), currency);

        List<ComputedLine> lines = new ArrayList<>();
        for (int i = 0; i < proposed.size(); i++) {
            lines.add(new ComputedLine(proposed.get(i), taxPerLine[i]));
        }

        Money beforeCredit = subtotal.plus(tax);
        Money creditApplied = Money.zero(currency);
        if (beforeCredit.isPositive() && creditAvailable.isPositive()) {
            creditApplied = creditAvailable.min(beforeCredit);
            lines.add(new ComputedLine(new ProposedLine(LineType.CREDIT_BALANCE, "Credit balance applied", 1,
                    creditApplied.negate(), creditApplied.negate(), today, today, false), Money.zero(currency)));
        }
        Money total = beforeCredit.minus(creditApplied);
        return new Result(List.copyOf(lines), subtotal, tax, creditApplied, total);
    }

    private static Money[] zeros(int n, java.util.Currency currency) {
        Money[] out = new Money[n];
        java.util.Arrays.fill(out, Money.zero(currency));
        return out;
    }
}

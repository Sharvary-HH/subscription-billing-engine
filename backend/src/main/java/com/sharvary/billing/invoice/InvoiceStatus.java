package com.sharvary.billing.invoice;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * DRAFT -> OPEN | VOID
 * OPEN  -> PAID | UNCOLLECTIBLE | VOID
 * PAID  -> REFUNDED
 * VOID, UNCOLLECTIBLE, REFUNDED -> (terminal)
 * </pre>
 * Once an invoice leaves DRAFT its lines and totals are frozen. Corrections are credit notes.
 */
public enum InvoiceStatus {
    DRAFT,
    OPEN,
    PAID,
    VOID,
    UNCOLLECTIBLE,
    REFUNDED;

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(OPEN, VOID),
            OPEN, EnumSet.of(PAID, UNCOLLECTIBLE, VOID),
            PAID, EnumSet.of(REFUNDED),
            VOID, EnumSet.noneOf(InvoiceStatus.class),
            UNCOLLECTIBLE, EnumSet.noneOf(InvoiceStatus.class),
            REFUNDED, EnumSet.noneOf(InvoiceStatus.class)
    );

    public boolean canTransitionTo(InvoiceStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isIssued() {
        return this != DRAFT;
    }
}

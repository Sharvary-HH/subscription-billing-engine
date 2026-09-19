package com.sharvary.billing.invoice;

/** RECURRING invoices are one-per-period per subscription; the others are not subject to that rule. */
public enum InvoiceKind {
    RECURRING,
    PRORATION,
    FINAL
}

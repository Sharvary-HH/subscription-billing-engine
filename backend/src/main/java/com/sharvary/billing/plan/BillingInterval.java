package com.sharvary.billing.plan;

public enum BillingInterval {
    MONTHLY(1),
    ANNUAL(12);

    private final int months;

    BillingInterval(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}

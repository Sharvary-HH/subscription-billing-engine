package com.sharvary.billing.plan;

/**
 * FLAT and PER_SEAT are billed in advance from the base price. TIERED and VOLUME are metered:
 * billed in arrears from usage records, using the plan version's tiers.
 */
public enum PricingModel {
    FLAT,
    PER_SEAT,
    TIERED,
    VOLUME;

    public boolean isMetered() {
        return this == TIERED || this == VOLUME;
    }
}

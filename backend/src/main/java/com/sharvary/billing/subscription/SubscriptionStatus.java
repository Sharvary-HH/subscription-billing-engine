package com.sharvary.billing.subscription;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The subscription lifecycle. Every legal move is listed here and nowhere else; the entity asks
 * this enum before changing state and throws if the answer is no.
 *
 * <pre>
 * TRIALING -> ACTIVE | CANCELED
 * ACTIVE   -> PAST_DUE | PAUSED | CANCELED
 * PAST_DUE -> ACTIVE | CANCELED
 * PAUSED   -> ACTIVE | CANCELED
 * CANCELED -> (terminal)
 * </pre>
 */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    PAUSED,
    CANCELED;

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> TRANSITIONS = Map.of(
            TRIALING, EnumSet.of(ACTIVE, CANCELED),
            ACTIVE, EnumSet.of(PAST_DUE, PAUSED, CANCELED),
            PAST_DUE, EnumSet.of(ACTIVE, CANCELED),
            PAUSED, EnumSet.of(ACTIVE, CANCELED),
            CANCELED, EnumSet.noneOf(SubscriptionStatus.class)
    );

    public boolean canTransitionTo(SubscriptionStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    /** States the recurring billing job will pick up. */
    public boolean isBillable() {
        return this == TRIALING || this == ACTIVE || this == PAST_DUE;
    }
}

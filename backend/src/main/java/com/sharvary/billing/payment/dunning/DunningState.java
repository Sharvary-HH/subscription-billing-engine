package com.sharvary.billing.payment.dunning;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * RETRYING -> RECOVERED | EXHAUSTED | CANCELED
 * RECOVERED, EXHAUSTED, CANCELED -> (terminal)
 * </pre>
 * A case is opened in RETRYING when an invoice's first charge fails, and leaves it exactly once.
 */
public enum DunningState {
    RETRYING,
    RECOVERED,
    EXHAUSTED,
    CANCELED;

    private static final Map<DunningState, Set<DunningState>> TRANSITIONS = Map.of(
            RETRYING, EnumSet.of(RECOVERED, EXHAUSTED, CANCELED),
            RECOVERED, EnumSet.noneOf(DunningState.class),
            EXHAUSTED, EnumSet.noneOf(DunningState.class),
            CANCELED, EnumSet.noneOf(DunningState.class)
    );

    public boolean canTransitionTo(DunningState next) {
        return TRANSITIONS.get(this).contains(next);
    }
}

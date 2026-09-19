package com.sharvary.billing.common;

public class IllegalStateTransitionException extends IllegalStateException {

    public IllegalStateTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super(entity + " cannot move from " + from + " to " + to);
    }
}

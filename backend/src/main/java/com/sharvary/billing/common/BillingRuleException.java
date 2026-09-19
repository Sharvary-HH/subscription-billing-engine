package com.sharvary.billing.common;

/** A request that is well-formed but breaks a business rule (422). */
public class BillingRuleException extends RuntimeException {

    public BillingRuleException(String message) {
        super(message);
    }
}

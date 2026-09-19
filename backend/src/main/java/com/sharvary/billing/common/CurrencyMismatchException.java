package com.sharvary.billing.common;

import java.util.Currency;

public class CurrencyMismatchException extends IllegalArgumentException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("cannot combine " + left.getCurrencyCode() + " with " + right.getCurrencyCode());
    }
}

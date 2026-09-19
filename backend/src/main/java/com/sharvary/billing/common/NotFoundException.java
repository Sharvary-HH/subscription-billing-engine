package com.sharvary.billing.common;

import java.util.UUID;

/**
 * Thrown for any lookup that misses, including lookups scoped to a customer who does not own the
 * row. Both cases return 404 on purpose: a 403 would confirm the row exists.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, UUID id) {
        super(what + " " + id + " not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}

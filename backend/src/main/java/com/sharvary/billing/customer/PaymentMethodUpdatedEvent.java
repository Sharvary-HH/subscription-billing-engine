package com.sharvary.billing.customer;

import java.util.UUID;

/**
 * Published when a customer adds or switches their default card. Dunning listens for this and
 * retries any open case immediately rather than waiting for the next scheduled attempt.
 */
public record PaymentMethodUpdatedEvent(UUID customerId) {
}

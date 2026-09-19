package com.sharvary.billing.payment;

import com.sharvary.billing.common.Money;

/**
 * The seam between billing and whoever actually moves money. The only implementation in this
 * repository is a mock, and that is deliberate: the interesting engineering is in what billing
 * does with a DECLINED or, more importantly, a TIMED_OUT answer.
 */
public interface PaymentProvider {

    enum Outcome {
        SUCCEEDED,
        DECLINED,
        /** The provider did not answer. The charge may or may not have gone through. */
        TIMED_OUT
    }

    record ChargeRequest(String idempotencyKey, Money amount, String paymentMethodToken, String description) {
    }

    record ChargeResult(Outcome outcome, String providerReference, String failureReason) {

        public static ChargeResult succeeded(String reference) {
            return new ChargeResult(Outcome.SUCCEEDED, reference, null);
        }

        public static ChargeResult declined(String reason) {
            return new ChargeResult(Outcome.DECLINED, null, reason);
        }

        public static ChargeResult timedOut() {
            return new ChargeResult(Outcome.TIMED_OUT, null, "provider timeout");
        }
    }

    record RefundRequest(String idempotencyKey, Money amount, String originalReference) {
    }

    /**
     * Charges the card. Must be idempotent on {@code idempotencyKey}: a second call with the same
     * key returns the outcome of the first without charging again.
     */
    ChargeResult charge(ChargeRequest request);

    void refund(RefundRequest request);
}

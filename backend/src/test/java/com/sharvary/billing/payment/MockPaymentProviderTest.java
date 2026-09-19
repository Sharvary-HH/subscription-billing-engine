package com.sharvary.billing.payment;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.payment.PaymentProvider.ChargeRequest;
import com.sharvary.billing.payment.PaymentProvider.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentProviderTest {

    static ChargeRequest req(String key, String token) {
        return new ChargeRequest(key, Money.of(1000, "USD"), token, "INV-1");
    }

    @Test
    void sameKeyNeverChargesTwice() {
        MockPaymentProvider p = new MockPaymentProvider();
        var first = p.charge(req("k1", "tok_ok"));
        var second = p.charge(req("k1", "tok_ok"));
        assertThat(first.outcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(second).isEqualTo(first);
        assertThat(p.chargesMade()).isEqualTo(1);
    }

    @Test
    void timeoutThatActuallyChargedIsRevealedOnRetryWithoutASecondCharge() {
        MockPaymentProvider p = new MockPaymentProvider();
        p.setMode(MockPaymentProvider.Mode.TIMEOUT);
        var first = p.charge(req("k2", "tok_visa"));
        assertThat(first.outcome()).isEqualTo(Outcome.TIMED_OUT);
        assertThat(p.chargesMade()).isEqualTo(1);

        p.setMode(MockPaymentProvider.Mode.SUCCEED);
        var retry = p.charge(req("k2", "tok_visa"));
        assertThat(retry.outcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(retry.providerReference()).isNotBlank();
        assertThat(p.chargesMade()).as("the retry must not charge again").isEqualTo(1);
    }

    @Test
    void timeoutThatDidNotChargeStaysATimeoutOnReplay() {
        MockPaymentProvider p = new MockPaymentProvider();
        p.setTimeoutActuallyCharges(false);
        p.setMode(MockPaymentProvider.Mode.TIMEOUT);
        p.charge(req("k3", "tok_visa"));
        assertThat(p.chargesMade()).isZero();
        assertThat(p.charge(req("k3", "tok_visa")).outcome()).isEqualTo(Outcome.TIMED_OUT);
    }

    @Test
    void tokenOverridesWinOverMode() {
        MockPaymentProvider p = new MockPaymentProvider();
        p.setMode(MockPaymentProvider.Mode.DECLINE);
        assertThat(p.charge(req("a", "tok_ok")).outcome()).isEqualTo(Outcome.SUCCEEDED);
        p.setMode(MockPaymentProvider.Mode.SUCCEED);
        assertThat(p.charge(req("b", "tok_decline")).outcome()).isEqualTo(Outcome.DECLINED);
        assertThat(p.charge(req("b", "tok_decline")).failureReason()).isEqualTo("card_declined");
        assertThat(p.charge(req("c", "tok_timeout")).outcome()).isEqualTo(Outcome.TIMED_OUT);
        assertThat(p.charge(req("d", "tok_visa")).outcome()).isEqualTo(Outcome.SUCCEEDED);
    }

    @Test
    void resetClearsEverything() {
        MockPaymentProvider p = new MockPaymentProvider();
        p.setMode(MockPaymentProvider.Mode.DECLINE);
        p.charge(req("x", "tok_ok"));
        p.refund(new PaymentProvider.RefundRequest("r", Money.of(1, "USD"), "ch"));
        assertThat(p.refundsMade()).isEqualTo(1);
        p.reset();
        assertThat(p.getMode()).isEqualTo(MockPaymentProvider.Mode.SUCCEED);
        assertThat(p.chargesMade()).isZero();
        assertThat(p.refundsMade()).isZero();
    }
}

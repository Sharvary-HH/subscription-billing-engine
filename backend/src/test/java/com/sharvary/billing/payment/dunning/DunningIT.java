package com.sharvary.billing.payment.dunning;

import com.sharvary.billing.config.MutableClock;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.customer.CustomerService;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.invoice.InvoiceRepository;
import com.sharvary.billing.invoice.InvoiceStatus;
import com.sharvary.billing.payment.MockPaymentProvider;
import com.sharvary.billing.payment.NotificationRepository;
import com.sharvary.billing.payment.PaymentAttempt;
import com.sharvary.billing.payment.PaymentAttemptRepository;
import com.sharvary.billing.payment.PaymentService;
import com.sharvary.billing.subscription.BillingService;
import com.sharvary.billing.subscription.Subscription;
import com.sharvary.billing.subscription.SubscriptionRepository;
import com.sharvary.billing.subscription.SubscriptionStatus;
import com.sharvary.billing.support.Fixtures;
import com.sharvary.billing.support.PostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 3's "done when", plus every case that breaks a naive flag-based implementation. */
class DunningIT extends PostgresIT {

    @Autowired Fixtures fixtures;
    @Autowired BillingService billing;
    @Autowired CustomerService customers;
    @Autowired PaymentService payments;
    @Autowired DunningJob dunningJob;
    @Autowired DunningCaseRepository cases;
    @Autowired InvoiceRepository invoices;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired NotificationRepository notifications;
    @Autowired MutableClock clock;
    @Autowired MockPaymentProvider provider;

    static final Instant T0 = LocalDate.of(2026, 3, 1).atTime(9, 0).toInstant(ZoneOffset.UTC);

    @BeforeEach
    void reset() {
        fixtures.wipe();
        provider.reset();
        clock.set(T0);
    }

    private Subscription failingSubscription() {
        Customer c = fixtures.customerWithCard("tok_decline");
        return billing.subscribe(c.getId(), fixtures.flatMonthly(2000).getId(), 1, 0, List.of());
    }

    private Invoice invoiceOf(Subscription s) {
        return invoices.findBySubscriptionIdOrderByCreatedAtAsc(s.getId()).get(0);
    }

    private DunningCase caseOf(Invoice i) {
        return cases.findByInvoiceId(i.getId()).orElseThrow();
    }

    @Test
    void firstFailureOpensACaseAndMarksTheSubscriptionPastDue() {
        Subscription sub = failingSubscription();
        Invoice invoice = invoiceOf(sub);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(subscriptions.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        DunningCase c = caseOf(invoice);
        assertThat(c.getState()).isEqualTo(DunningState.RETRYING);
        assertThat(c.getRetriesDone()).isZero();
        assertThat(c.getNextRetryAt()).isEqualTo(T0.plus(Duration.ofDays(1)));
        assertThat(notifications.findByCustomerIdOrderByCreatedAtDesc(sub.getCustomer().getId()))
                .extracting(n -> n.getKind()).containsExactly("payment_failed");
    }

    @Test
    void fourRetriesFireOnScheduleThenTheSubscriptionIsSuspended() {
        Subscription sub = failingSubscription();
        Invoice invoice = invoiceOf(sub);

        // Nothing fires before day 1.
        clock.set(T0.plus(Duration.ofHours(20)));
        assertThat(dunningJob.runOnce()).isZero();

        int[] days = {1, 3, 5, 7};
        for (int i = 0; i < days.length; i++) {
            clock.set(T0.plus(Duration.ofDays(days[i])).plus(Duration.ofMinutes(1)));
            assertThat(dunningJob.runOnce()).as("retry %d", i + 1).isEqualTo(1);
            assertThat(dunningJob.runOnce()).as("retry %d must not fire twice", i + 1).isZero();
            assertThat(attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId())).hasSize(i + 2);
        }

        DunningCase c = caseOf(invoice);
        assertThat(c.getState()).isEqualTo(DunningState.EXHAUSTED);
        assertThat(c.getRetriesDone()).isEqualTo(4);
        assertThat(c.getNextRetryAt()).isNull();
        assertThat(invoices.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.UNCOLLECTIBLE);
        assertThat(subscriptions.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(notifications.findByCustomerIdOrderByCreatedAtDesc(sub.getCustomer().getId()))
                .extracting(n -> n.getKind()).contains("subscription_suspended");

        // Long after, nothing else ever fires for it.
        clock.set(T0.plus(Duration.ofDays(60)));
        assertThat(dunningJob.runOnce()).isZero();
        assertThat(attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId())).hasSize(5);
    }

    @Test
    void successOnRetryThreeMeansRetryFourNeverFires() {
        Subscription sub = failingSubscription();
        Invoice invoice = invoiceOf(sub);
        clock.set(T0.plus(Duration.ofDays(1)).plusSeconds(1));
        dunningJob.runOnce();
        clock.set(T0.plus(Duration.ofDays(3)).plusSeconds(1));
        dunningJob.runOnce();

        provider.setMode(MockPaymentProvider.Mode.SUCCEED);
        // Token overrides mode, so swap the card for one that works without going through the
        // card-updated path (that path is its own test below).
        customers.addPaymentMethod(sub.getCustomer().getId(), "tok_ok", "visa", "1111", false);
        // A non-default card changes nothing; make it the default via the plain repository path.
        var methods = customers.paymentMethods(sub.getCustomer().getId());
        customers.setDefault(sub.getCustomer().getId(), methods.get(1).getId());
        // setDefault triggers an immediate retry, which succeeds.
        assertThat(invoices.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(caseOf(invoice).getState()).isEqualTo(DunningState.RECOVERED);
        assertThat(subscriptions.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        clock.set(T0.plus(Duration.ofDays(30)));
        assertThat(dunningJob.runOnce()).isZero();
        List<PaymentAttempt> all = attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId());
        assertThat(all).hasSize(4);
        assertThat(all.get(3).getStatus()).isEqualTo(PaymentAttempt.Status.SUCCEEDED);
        assertThat(all.get(3).getTriggeredBy()).isEqualTo(PaymentService.TRIGGER_CARD_UPDATED);
    }

    @Test
    void updatingTheCardOnDayFourRetriesImmediatelyAndResetsTheSchedule() {
        Subscription sub = failingSubscription();
        Invoice invoice = invoiceOf(sub);
        clock.set(T0.plus(Duration.ofDays(1)).plusSeconds(1));
        dunningJob.runOnce();
        clock.set(T0.plus(Duration.ofDays(3)).plusSeconds(1));
        dunningJob.runOnce();
        assertThat(caseOf(invoice).getRetriesDone()).isEqualTo(2);

        Instant day4 = T0.plus(Duration.ofDays(4));
        clock.set(day4);
        // New card that still declines: the retry fires now, fails, and the week starts over.
        customers.addPaymentMethod(sub.getCustomer().getId(), "tok_decline", "mastercard", "2222", true);

        List<PaymentAttempt> all = attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId());
        assertThat(all).hasSize(4);
        assertThat(all.get(3).getTriggeredBy()).isEqualTo(PaymentService.TRIGGER_CARD_UPDATED);
        assertThat(all.get(3).getCreatedAt()).isEqualTo(day4);
        DunningCase c = caseOf(invoice);
        assertThat(c.getState()).isEqualTo(DunningState.RETRYING);
        assertThat(c.getStartedAt()).isEqualTo(day4);
        assertThat(c.getRetriesDone()).isEqualTo(1);
        assertThat(c.getNextRetryAt()).isEqualTo(day4.plus(Duration.ofDays(3)));
        assertThat(notifications.findByCustomerIdOrderByCreatedAtDesc(sub.getCustomer().getId()))
                .extracting(n -> n.getKind()).contains("card_updated_retry");
    }

    @Test
    void cancelingMidDunningStopsRetriesAndTheDebtSurvives() {
        Subscription sub = failingSubscription();
        Invoice invoice = invoiceOf(sub);
        clock.set(T0.plus(Duration.ofDays(1)).plusSeconds(1));
        dunningJob.runOnce();

        billing.cancel(sub.getId(), true);
        assertThat(caseOf(invoice).getState()).isEqualTo(DunningState.CANCELED);
        assertThat(invoices.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.OPEN);

        clock.set(T0.plus(Duration.ofDays(10)));
        assertThat(dunningJob.runOnce()).isZero();
        assertThat(attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId())).hasSize(2);

        // An admin can still collect it by hand later, once the card works.
        provider.setMode(MockPaymentProvider.Mode.SUCCEED);
        customers.addPaymentMethod(sub.getCustomer().getId(), "tok_ok", "visa", "3333", true);
        // The card update does not retry: the case is closed. Manual collection does.
        assertThat(attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId())).hasSize(2);
        payments.collect(invoice.getId(), PaymentService.TRIGGER_MANUAL);
        assertThat(invoices.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        // Recovered-after-cancel does not resurrect the subscription.
        assertThat(subscriptions.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void aRetryAfterATimeoutReusesTheKeyAndNeverDoubleCharges() {
        Customer c = fixtures.customerWithCard("tok_visa");
        provider.setMode(MockPaymentProvider.Mode.TIMEOUT);
        Subscription sub = billing.subscribe(c.getId(), fixtures.flatMonthly(2000).getId(), 1, 0, List.of());
        Invoice invoice = invoiceOf(sub);
        List<PaymentAttempt> first = attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId());
        assertThat(first.get(0).getStatus()).isEqualTo(PaymentAttempt.Status.TIMED_OUT);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(caseOf(invoice).getState()).isEqualTo(DunningState.RETRYING);
        assertThat(provider.chargesMade()).isEqualTo(1); // it did go through on the provider side

        provider.setMode(MockPaymentProvider.Mode.SUCCEED);
        clock.set(T0.plus(Duration.ofDays(1)).plusSeconds(1));
        dunningJob.runOnce();
        List<PaymentAttempt> all = attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId());
        assertThat(all).hasSize(2);
        assertThat(all.get(1).getIdempotencyKey()).isEqualTo(all.get(0).getIdempotencyKey());
        assertThat(all.get(1).getStatus()).isEqualTo(PaymentAttempt.Status.SUCCEEDED);
        assertThat(provider.chargesMade()).as("the customer was charged once").isEqualTo(1);
        assertThat(invoices.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(caseOf(invoice).getState()).isEqualTo(DunningState.RECOVERED);
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void aScheduledRetryAndAManualPaymentAtTheSameMomentOnlyChargeOnce() throws Exception {
        Subscription sub = failingSubscription();
        Invoice invoice = invoiceOf(sub);
        // Quietly swap in a working card (SQL, so the card-updated retry does not fire and the
        // invoice stays OPEN with a retry due on day 1).
        customers.addPaymentMethod(sub.getCustomer().getId(), "tok_ok", "visa", "6666", false);
        UUID okCard = customers.paymentMethods(sub.getCustomer().getId()).get(1).getId();
        jdbc.update("update payment_methods set is_default = false where customer_id = ?", sub.getCustomer().getId());
        jdbc.update("update payment_methods set is_default = true where id = ?", okCard);
        clock.set(T0.plus(Duration.ofDays(1)).plusSeconds(1));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> manual = pool.submit(() -> { go.await(); return payments.collect(invoice.getId(), PaymentService.TRIGGER_MANUAL); });
        Future<?> retry = pool.submit(() -> { go.await(); return dunningJob.runOnce(); });
        go.countDown();
        manual.get();
        retry.get();
        pool.shutdown();

        List<PaymentAttempt> all = attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId());
        long succeeded = all.stream().filter(x -> x.getStatus() == PaymentAttempt.Status.SUCCEEDED).count();
        assertThat(succeeded).as("only one of the two may win").isEqualTo(1);
        assertThat(all).hasSize(2);
        assertThat(provider.chargesMade()).isEqualTo(1);
        assertThat(invoices.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(caseOf(invoice).getState()).isEqualTo(DunningState.RECOVERED);
    }

    @Test
    void noPaymentMethodIsAFailureNotACrash() {
        Customer c = customers.create("No Card", "nocard-" + UUID.randomUUID() + "@test.local", Fixtures.USD, "US");
        Subscription sub = billing.subscribe(c.getId(), fixtures.flatMonthly(500).getId(), 1, 0, List.of());
        Invoice invoice = invoiceOf(sub);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        List<PaymentAttempt> all = attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoice.getId());
        assertThat(all.get(0).getFailureReason()).isEqualTo("no_payment_method");
        assertThat(caseOf(invoice).getState()).isEqualTo(DunningState.RETRYING);
        // Collecting a non-open invoice is a no-op.
        assertThat(payments.collect(invoiceOf(failingSubscription()).getId(), "x")).isPresent();
        Invoice paid = invoiceOf(billing.subscribe(fixtures.customer("US").getId(), fixtures.flatMonthly(1).getId(), 1, 0, List.of()));
        assertThat(payments.collect(paid.getId(), "x")).isEmpty();
    }
}

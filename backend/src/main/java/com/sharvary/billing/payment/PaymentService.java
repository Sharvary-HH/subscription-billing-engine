package com.sharvary.billing.payment;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.common.NotFoundException;
import com.sharvary.billing.customer.PaymentMethod;
import com.sharvary.billing.customer.PaymentMethodRepository;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.invoice.InvoiceRepository;
import com.sharvary.billing.invoice.InvoiceStatus;
import com.sharvary.billing.payment.dunning.DunningService;
import com.sharvary.billing.subscription.Subscription;
import com.sharvary.billing.subscription.SubscriptionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Collects payment for an open invoice. Every path that charges a card (first attempt, scheduled
 * retry, immediate retry after a card update, manual "pay now") comes through {@link #collect}.
 *
 * <p>Two attempts cannot race: the invoice row is locked for the duration, and the first one to
 * commit moves the invoice out of OPEN, so the second sees nothing to do.
 */
@Service
public class PaymentService {

    public static final String TRIGGER_INITIAL = "initial";
    public static final String TRIGGER_RETRY = "dunning_retry";
    public static final String TRIGGER_CARD_UPDATED = "card_updated";
    public static final String TRIGGER_MANUAL = "manual";

    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final PaymentMethodRepository paymentMethods;
    private final PaymentProvider provider;
    private final DunningService dunning;
    private final Clock clock;

    public PaymentService(InvoiceRepository invoices, PaymentAttemptRepository attempts,
                          PaymentMethodRepository paymentMethods, PaymentProvider provider, DunningService dunning,
                          Clock clock) {
        this.invoices = invoices;
        this.attempts = attempts;
        this.paymentMethods = paymentMethods;
        this.provider = provider;
        this.dunning = dunning;
        this.clock = clock;
    }

    /**
     * @return the attempt that was made, or empty if the invoice was no longer collectable (already
     * paid by a concurrent attempt, voided, and so on).
     */
    @Transactional
    public Optional<PaymentAttempt> collect(UUID invoiceId, String trigger) {
        Invoice invoice = invoices.findByIdForUpdate(invoiceId).orElseThrow(() -> new NotFoundException("invoice", invoiceId));
        if (invoice.getStatus() != InvoiceStatus.OPEN) {
            return Optional.empty();
        }

        Optional<PaymentAttempt> previous = attempts.findFirstByInvoiceIdOrderByAttemptNumberDesc(invoiceId);
        int attemptNumber = previous.map(a -> a.getAttemptNumber() + 1).orElse(1);
        // After a timeout we do not know whether the charge landed, so the retry reuses the key.
        // The provider then tells us what actually happened instead of charging a second time.
        String key = previous.filter(a -> a.getStatus() == PaymentAttempt.Status.TIMED_OUT)
                .map(PaymentAttempt::getIdempotencyKey)
                .orElse(invoiceId + ":" + attemptNumber);

        Money due = invoice.amountDue();
        Optional<PaymentMethod> method = paymentMethods.findByCustomerIdAndDefaultMethodTrue(invoice.getCustomer().getId());
        PaymentProvider.ChargeResult result = method
                .map(m -> provider.charge(new PaymentProvider.ChargeRequest(key, due, m.getProviderToken(),
                        invoice.getInvoiceNumber())))
                .orElse(PaymentProvider.ChargeResult.declined("no_payment_method"));

        PaymentAttempt attempt = attempts.save(new PaymentAttempt(UUID.randomUUID(), invoice, attemptNumber, key, due,
                toStatus(result.outcome()), result.providerReference(), result.failureReason(), trigger, clock.instant()));

        switch (result.outcome()) {
            case SUCCEEDED -> onSucceeded(invoice, due);
            case DECLINED, TIMED_OUT -> dunning.onPaymentFailed(invoice, attempt);
        }
        return Optional.of(attempt);
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> attemptsFor(UUID invoiceId) {
        return attempts.findByInvoiceIdOrderByAttemptNumberAsc(invoiceId);
    }

    private void onSucceeded(Invoice invoice, Money amount) {
        invoice.markPaid(amount, clock.instant());
        dunning.onPaymentRecovered(invoice);
        Subscription subscription = invoice.getSubscription();
        if (subscription != null && subscription.getStatus() == SubscriptionStatus.PAST_DUE
                && dunning.noOpenCases(subscription.getId())) {
            subscription.transitionTo(SubscriptionStatus.ACTIVE);
        }
    }

    private static PaymentAttempt.Status toStatus(PaymentProvider.Outcome outcome) {
        return switch (outcome) {
            case SUCCEEDED -> PaymentAttempt.Status.SUCCEEDED;
            case DECLINED -> PaymentAttempt.Status.FAILED;
            case TIMED_OUT -> PaymentAttempt.Status.TIMED_OUT;
        };
    }
}

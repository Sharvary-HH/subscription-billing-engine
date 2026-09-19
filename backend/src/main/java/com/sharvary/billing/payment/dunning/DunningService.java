package com.sharvary.billing.payment.dunning;

import com.sharvary.billing.customer.PaymentMethodUpdatedEvent;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.payment.NotificationService;
import com.sharvary.billing.payment.PaymentAttempt;
import com.sharvary.billing.subscription.Subscription;
import com.sharvary.billing.subscription.SubscriptionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Drives {@link DunningCase} through its states. The rules, in one place:
 *
 * <ul>
 *   <li>First failure on an invoice opens a case, schedules retry 1, moves the subscription to PAST_DUE.</li>
 *   <li>A failed retry schedules the next one from the original start time; after the last one the
 *       case is EXHAUSTED, the invoice UNCOLLECTIBLE and the subscription CANCELED.</li>
 *   <li>A successful charge at any point resolves the case as RECOVERED. Nothing further fires.</li>
 *   <li>A card update restarts the schedule and asks for an immediate retry.</li>
 *   <li>Cancelling the subscription closes the case as CANCELED. The invoice stays OPEN: the debt
 *       is real and an admin can still collect or void it.</li>
 * </ul>
 */
@Service
public class DunningService {

    private final DunningCaseRepository cases;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final RetrySchedule schedule;
    private final Clock clock;

    public DunningService(DunningCaseRepository cases, NotificationService notifications,
                          ApplicationEventPublisher events, Clock clock,
                          @Value("${billing.dunning.retry-days:1,3,5,7}") String retryDays) {
        this.cases = cases;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
        this.schedule = RetrySchedule.parse(retryDays);
    }

    public RetrySchedule schedule() {
        return schedule;
    }

    @Transactional
    public void onPaymentFailed(Invoice invoice, PaymentAttempt attempt) {
        Instant now = clock.instant();
        Subscription subscription = invoice.getSubscription();
        UUID customerId = invoice.getCustomer().getId();

        DunningCase dunningCase = cases.findByInvoiceId(invoice.getId()).orElse(null);
        if (dunningCase == null) {
            Instant firstRetry = schedule.nextRetryAt(now, 0).orElseThrow();
            dunningCase = cases.save(new DunningCase(UUID.randomUUID(), invoice, subscription, now, firstRetry));
            if (subscription != null && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                subscription.transitionTo(SubscriptionStatus.PAST_DUE);
            }
            notifications.send(customerId, "payment_failed",
                    "Payment for " + invoice.getInvoiceNumber() + " failed",
                    "We could not charge your card (" + attempt.getFailureReason() + "). We will retry on "
                            + day(firstRetry) + ". Update your card to retry now.");
            return;
        }

        if (dunningCase.getState() != DunningState.RETRYING) {
            return;
        }
        boolean more = dunningCase.recordFailedRetry(schedule);
        if (more) {
            notifications.send(customerId, "payment_retry_failed",
                    "Retry " + dunningCase.getRetriesDone() + " for " + invoice.getInvoiceNumber() + " failed",
                    "Next retry on " + day(dunningCase.getNextRetryAt()) + ".");
            return;
        }

        dunningCase.resolve(DunningState.EXHAUSTED, now);
        invoice.markUncollectible();
        if (subscription != null && subscription.getStatus().canTransitionTo(SubscriptionStatus.CANCELED)) {
            subscription.cancelNow(now, java.time.LocalDate.ofInstant(now, java.time.ZoneOffset.UTC));
        }
        notifications.send(customerId, "subscription_suspended",
                "Your subscription has been suspended",
                "After " + schedule.maxRetries() + " failed retries, " + invoice.getInvoiceNumber()
                        + " was written off and your subscription was canceled.");
    }

    @Transactional
    public void onPaymentRecovered(Invoice invoice) {
        cases.findByInvoiceId(invoice.getId())
                .filter(c -> c.getState() == DunningState.RETRYING)
                .ifPresent(c -> {
                    c.resolve(DunningState.RECOVERED, clock.instant());
                    notifications.send(invoice.getCustomer().getId(), "payment_recovered",
                            "Payment for " + invoice.getInvoiceNumber() + " received", "Thanks, you are all set.");
                });
    }

    @Transactional
    public void onSubscriptionCanceled(UUID subscriptionId) {
        for (DunningCase c : cases.findBySubscriptionIdAndState(subscriptionId, DunningState.RETRYING)) {
            c.resolve(DunningState.CANCELED, clock.instant());
        }
    }

    /**
     * A card update resets every open case for the customer and requests an immediate retry.
     * The retry itself is published as an event and run by {@link DunningJob}, which keeps this
     * service free of a dependency on the payment service (which depends on this one).
     */
    @EventListener
    @Transactional
    public void onPaymentMethodUpdated(PaymentMethodUpdatedEvent event) {
        Instant now = clock.instant();
        for (DunningCase c : cases.findByStateAndCustomer(DunningState.RETRYING, event.customerId())) {
            c.restart(now);
            notifications.send(event.customerId(), "card_updated_retry",
                    "Retrying payment for " + c.getInvoice().getInvoiceNumber(),
                    "Thanks for updating your card. We are retrying the payment now.");
            events.publishEvent(new RetryRequestedEvent(c.getInvoice().getId()));
        }
    }

    @Transactional(readOnly = true)
    public boolean noOpenCases(UUID subscriptionId) {
        return cases.findBySubscriptionIdAndState(subscriptionId, DunningState.RETRYING).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<DunningCase> queue() {
        return cases.findByStateOrderByNextRetryAtAsc(DunningState.RETRYING);
    }

    @Transactional(readOnly = true)
    public List<DunningCase> all() {
        return cases.findAll();
    }

    private static String day(Instant at) {
        return java.time.LocalDate.ofInstant(at, java.time.ZoneOffset.UTC).toString();
    }

    /** Asks for an invoice to be retried as soon as the current transaction commits. */
    public record RetryRequestedEvent(UUID invoiceId) {
    }
}

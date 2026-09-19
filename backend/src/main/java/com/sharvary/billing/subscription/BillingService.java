package com.sharvary.billing.subscription;

import com.sharvary.billing.common.BillingRuleException;
import com.sharvary.billing.common.Money;
import com.sharvary.billing.common.NotFoundException;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.customer.CustomerRepository;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.invoice.InvoiceKind;
import com.sharvary.billing.invoice.InvoiceService;
import com.sharvary.billing.invoice.LineType;
import com.sharvary.billing.invoice.allocation.InvoiceCalculator;
import com.sharvary.billing.invoice.allocation.InvoiceCalculator.ProposedLine;
import com.sharvary.billing.payment.PaymentService;
import com.sharvary.billing.payment.dunning.DunningService;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.plan.PlanVersionRepository;
import com.sharvary.billing.subscription.proration.ProrationCalculator;
import com.sharvary.billing.usage.UsageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The subscription lifecycle: subscribe, roll periods, change plan or seats, cancel. This is the
 * service that decides what lines an invoice gets; the maths behind each line lives in the pure
 * calculators ({@link ProrationCalculator}, {@link InvoiceCalculator}, metered pricing).
 *
 * <p>Policy decisions, all recorded in docs/adr:
 * <ul>
 *   <li>Plan charges are billed in advance at the start of each period; usage is billed in arrears
 *       for the period that just closed, on the same invoice.</li>
 *   <li>An upgrade invoices immediately for the prorated difference. A downgrade credits the
 *       customer's balance and the credit is drawn on the next invoice. Nothing is refunded.</li>
 *   <li>Proration is daily.</li>
 * </ul>
 */
@Service
public class BillingService {

    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final PlanVersionRepository planVersions;
    private final InvoiceService invoices;
    private final PaymentService payments;
    private final DunningService dunning;
    private final UsageService usage;
    private final Clock clock;

    public BillingService(SubscriptionRepository subscriptions, CustomerRepository customers,
                          PlanVersionRepository planVersions, InvoiceService invoices, PaymentService payments,
                          DunningService dunning, UsageService usage, Clock clock) {
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.planVersions = planVersions;
        this.invoices = invoices;
        this.payments = payments;
        this.dunning = dunning;
        this.usage = usage;
        this.clock = clock;
    }

    // ---- subscribe -------------------------------------------------------------------------

    @Transactional
    public Subscription subscribe(UUID customerId, UUID planVersionId, int quantity, int trialDays,
                                  List<UUID> meteredAddOnVersionIds) {
        Customer customer = customers.findById(customerId).orElseThrow(() -> new NotFoundException("customer", customerId));
        PlanVersion version = planVersions.findById(planVersionId)
                .orElseThrow(() -> new NotFoundException("plan version", planVersionId));
        if (!version.getPlan().isActive()) {
            throw new BillingRuleException("plan " + version.getPlan().getCode() + " is retired");
        }
        if (!version.currency().equals(customer.currency())) {
            throw new BillingRuleException("plan is priced in " + version.currency() + " but the customer bills in "
                    + customer.currency());
        }
        if (version.getPricingModel().isMetered()) {
            throw new BillingRuleException("a metered plan cannot be the base plan; attach it as an add-on");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        if (trialDays < 0) {
            throw new IllegalArgumentException("trial days cannot be negative");
        }

        LocalDate today = today();
        Instant now = clock.instant();
        int anchorDay = today.getDayOfMonth();
        Subscription subscription;
        if (trialDays > 0) {
            LocalDate trialEnd = today.plusDays(trialDays);
            subscription = new Subscription(UUID.randomUUID(), customer, version, quantity, SubscriptionStatus.TRIALING,
                    anchorDay, today, trialEnd, trialEnd, now);
        } else {
            LocalDate periodEnd = BillingPeriods.periodEnd(today, anchorDay, version.getBillingInterval());
            subscription = new Subscription(UUID.randomUUID(), customer, version, quantity, SubscriptionStatus.ACTIVE,
                    anchorDay, today, periodEnd, null, now);
        }
        for (UUID addOnId : meteredAddOnVersionIds == null ? List.<UUID>of() : meteredAddOnVersionIds) {
            PlanVersion addOn = planVersions.findById(addOnId).orElseThrow(() -> new NotFoundException("plan version", addOnId));
            if (!addOn.getPricingModel().isMetered()) {
                throw new BillingRuleException("add-on " + addOn.getPlan().getCode() + " is not metered");
            }
            if (!addOn.currency().equals(customer.currency())) {
                throw new BillingRuleException("add-on currency does not match the customer's");
            }
            subscription.addItem(UUID.randomUUID(), addOn, 1, now);
        }
        subscription = subscriptions.save(subscription);

        if (trialDays == 0) {
            Invoice invoice = invoices.issue(customer, subscription, InvoiceKind.RECURRING, today,
                    subscription.getCurrentPeriodEnd(), List.of(planLine(subscription, today, subscription.getCurrentPeriodEnd())));
            collectIfOpen(invoice);
        }
        return subscription;
    }

    // ---- recurring billing -----------------------------------------------------------------

    /**
     * Rolls a due subscription forward, one period at a time, invoicing each one. Called by the
     * billing job with the subscription row already locked. If the clock has jumped several
     * months (the demo does this) every missed period gets its own invoice.
     */
    @Transactional
    public List<Invoice> rollForward(Subscription subscription) {
        LocalDate today = today();
        List<Invoice> produced = new ArrayList<>();
        while (subscription.getStatus().isBillable() && !subscription.getCurrentPeriodEnd().isAfter(today)) {
            Optional<Invoice> invoice = rollOnePeriod(subscription);
            invoice.ifPresent(produced::add);
        }
        return produced;
    }

    private Optional<Invoice> rollOnePeriod(Subscription subscription) {
        LocalDate closedStart = subscription.getCurrentPeriodStart();
        LocalDate closedEnd = subscription.getCurrentPeriodEnd();
        Customer customer = subscription.getCustomer();
        Instant now = clock.instant();

        if (subscription.isScheduledToCancelAtPeriodEnd()) {
            subscription.cancelNow(now, closedEnd);
            dunning.onSubscriptionCanceled(subscription.getId());
            List<ProposedLine> finalUsage = usageLines(subscription, closedStart, closedEnd);
            if (finalUsage.isEmpty()) {
                return Optional.empty();
            }
            Invoice invoice = invoices.issue(customer, subscription, InvoiceKind.FINAL, closedStart, closedEnd, finalUsage);
            collectIfOpen(invoice);
            return Optional.of(invoice);
        }

        boolean wasTrial = subscription.getStatus() == SubscriptionStatus.TRIALING;
        if (wasTrial) {
            subscription.endTrial();
        }

        LocalDate newStart = closedEnd;
        LocalDate newEnd = BillingPeriods.periodEnd(newStart, subscription.getAnchorDay(),
                subscription.getPlanVersion().getBillingInterval());
        subscription.rollPeriod(newStart, newEnd);

        List<ProposedLine> lines = new ArrayList<>();
        lines.add(planLine(subscription, newStart, newEnd));
        if (!wasTrial) {
            lines.addAll(usageLines(subscription, closedStart, closedEnd));
        }
        Invoice invoice = invoices.issue(customer, subscription, InvoiceKind.RECURRING, newStart, newEnd, lines);
        collectIfOpen(invoice);
        return Optional.of(invoice);
    }

    // ---- plan and quantity changes ---------------------------------------------------------

    public record ChangeResult(Subscription subscription, Optional<Invoice> invoice, Money credited, Money charged) {
    }

    /**
     * Moves the subscription to another plan version and/or seat count from today. The unused part
     * of the old price is credited, the unused part of the new price is charged. If that nets to a
     * charge (an upgrade) it is invoiced now; if it nets to a credit (a downgrade) the credit goes
     * on the customer's balance for the next invoice.
     */
    @Transactional
    public ChangeResult change(UUID subscriptionId, UUID newVersionId, int newQuantity) {
        Subscription subscription = subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new NotFoundException("subscription", subscriptionId));
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE && subscription.getStatus() != SubscriptionStatus.TRIALING) {
            throw new BillingRuleException("only active or trialing subscriptions can be changed; this one is "
                    + subscription.getStatus());
        }
        if (newQuantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        PlanVersion newVersion = newVersionId == null ? subscription.getPlanVersion()
                : planVersions.findById(newVersionId).orElseThrow(() -> new NotFoundException("plan version", newVersionId));
        if (newVersion.getPricingModel().isMetered()) {
            throw new BillingRuleException("a metered plan cannot be the base plan");
        }
        if (!newVersion.currency().equals(subscription.getCustomer().currency())) {
            throw new BillingRuleException("plan currency does not match the customer's");
        }
        if (newVersion.getBillingInterval() != subscription.getPlanVersion().getBillingInterval()) {
            throw new BillingRuleException("changing billing interval mid-cycle is not supported; cancel at period end and resubscribe");
        }
        if (newVersion.getId().equals(subscription.getPlanVersion().getId()) && newQuantity == subscription.getQuantity()) {
            throw new BillingRuleException("nothing to change");
        }

        LocalDate today = today();
        Customer customer = subscription.getCustomer();
        Money zero = Money.zero(customer.currency());

        if (subscription.getStatus() == SubscriptionStatus.TRIALING) {
            // Nothing has been paid yet, so there is nothing to prorate. Just switch.
            subscription.switchTo(newVersion, newQuantity);
            return new ChangeResult(subscription, Optional.empty(), zero, zero);
        }

        Money oldPrice = subscription.periodPrice();
        Money newPrice = newVersion.periodPrice(newQuantity);
        LocalDate periodStart = subscription.getCurrentPeriodStart();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd();
        ProrationCalculator.Result proration = ProrationCalculator.planChange(oldPrice, newPrice, periodStart, periodEnd, today);

        String oldName = describe(subscription.getPlanVersion(), subscription.getQuantity());
        subscription.switchTo(newVersion, newQuantity);
        String newName = describe(newVersion, newQuantity);

        if (proration.net().isPositive()) {
            List<ProposedLine> lines = new ArrayList<>();
            if (proration.credit().isPositive()) {
                lines.add(new ProposedLine(LineType.PRORATION_CREDIT, "Unused time on " + oldName, 1,
                        proration.credit().negate(), proration.credit().negate(), today, periodEnd, true));
            }
            lines.add(new ProposedLine(LineType.PRORATION_CHARGE, "Remaining time on " + newName, 1,
                    proration.charge(), proration.charge(), today, periodEnd, true));
            Invoice invoice = invoices.issue(customer, subscription, InvoiceKind.PRORATION, today, periodEnd, lines);
            collectIfOpen(invoice);
            return new ChangeResult(subscription, Optional.of(invoice), proration.credit(), proration.charge());
        }

        Money netCredit = proration.net().negate();
        if (netCredit.isPositive()) {
            Customer locked = customers.findByIdForUpdate(customer.getId()).orElseThrow();
            locked.addCredit(netCredit);
        }
        return new ChangeResult(subscription, Optional.empty(), proration.credit(), proration.charge());
    }

    // ---- cancellation and pause ------------------------------------------------------------

    @Transactional
    public Subscription cancel(UUID subscriptionId, boolean immediately) {
        Subscription subscription = subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new NotFoundException("subscription", subscriptionId));
        if (immediately) {
            subscription.cancelNow(clock.instant(), today());
            dunning.onSubscriptionCanceled(subscriptionId);
        } else {
            subscription.cancelAtPeriodEnd();
        }
        return subscription;
    }

    @Transactional
    public Subscription undoScheduledCancel(UUID subscriptionId) {
        Subscription subscription = subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new NotFoundException("subscription", subscriptionId));
        subscription.clearScheduledCancel();
        return subscription;
    }

    @Transactional
    public Subscription pause(UUID subscriptionId) {
        Subscription subscription = subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new NotFoundException("subscription", subscriptionId));
        subscription.transitionTo(SubscriptionStatus.PAUSED);
        return subscription;
    }

    /**
     * Resumes a paused subscription. If the paid-for period is still running nothing else happens.
     * If it has lapsed, a fresh period starts today, re-anchored to today, and is invoiced in full.
     * Keeping the old anchor would mean a stub period of a few days at the full price.
     */
    @Transactional
    public Subscription resume(UUID subscriptionId) {
        Subscription subscription = subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new NotFoundException("subscription", subscriptionId));
        subscription.transitionTo(SubscriptionStatus.ACTIVE);
        LocalDate today = today();
        if (!subscription.getCurrentPeriodEnd().isAfter(today)) {
            LocalDate end = BillingPeriods.periodEnd(today, today.getDayOfMonth(),
                    subscription.getPlanVersion().getBillingInterval());
            subscription.restartOn(today, end);
            Invoice invoice = invoices.issue(subscription.getCustomer(), subscription, InvoiceKind.RECURRING, today, end,
                    List.of(planLine(subscription, today, end)));
            collectIfOpen(invoice);
        }
        return subscription;
    }

    // ---- queries ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Subscription get(UUID id) {
        return subscriptions.findById(id).orElseThrow(() -> new NotFoundException("subscription", id));
    }

    @Transactional(readOnly = true)
    public Subscription getForCustomer(UUID id, UUID customerId) {
        return subscriptions.findByIdAndCustomerId(id, customerId).orElseThrow(() -> new NotFoundException("subscription", id));
    }

    @Transactional(readOnly = true)
    public List<Subscription> listForCustomer(UUID customerId) {
        return subscriptions.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Subscription> search(String query, SubscriptionStatus status) {
        String pattern = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        List<SubscriptionStatus> statuses = status == null ? List.of(SubscriptionStatus.values()) : List.of(status);
        return subscriptions.search(pattern, statuses);
    }

    /** What the next recurring invoice would look like if the period rolled today, credit included. */
    @Transactional(readOnly = true)
    public InvoiceCalculator.Result estimateNextInvoice(UUID subscriptionId) {
        Subscription subscription = get(subscriptionId);
        if (!subscription.getStatus().isBillable() || subscription.isScheduledToCancelAtPeriodEnd()) {
            return invoices.estimate(subscription.getCustomer(), List.of());
        }
        LocalDate nextStart = subscription.getCurrentPeriodEnd();
        LocalDate nextEnd = BillingPeriods.periodEnd(nextStart, subscription.getAnchorDay(),
                subscription.getPlanVersion().getBillingInterval());
        List<ProposedLine> lines = new ArrayList<>();
        lines.add(planLine(subscription, nextStart, nextEnd));
        lines.addAll(usageLines(subscription, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd()));
        return invoices.estimate(subscription.getCustomer(), lines);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private ProposedLine planLine(Subscription subscription, LocalDate start, LocalDate end) {
        PlanVersion version = subscription.getPlanVersion();
        Money unit = version.getPricingModel() == com.sharvary.billing.plan.PricingModel.PER_SEAT
                ? version.basePrice() : version.periodPrice(subscription.getQuantity());
        long qty = version.getPricingModel() == com.sharvary.billing.plan.PricingModel.PER_SEAT ? subscription.getQuantity() : 1;
        return new ProposedLine(LineType.PLAN, describe(version, subscription.getQuantity()) + " (" + start + " to "
                + end.minusDays(1) + ")", qty, unit, subscription.periodPrice(), start, end, false);
    }

    private List<ProposedLine> usageLines(Subscription subscription, LocalDate from, LocalDate to) {
        List<ProposedLine> lines = new ArrayList<>();
        for (SubscriptionItem item : subscription.getItems()) {
            long quantity = usage.quantityFor(item.getId(), from, to);
            if (quantity == 0) {
                continue;
            }
            PlanVersion version = item.getPlanVersion();
            Money amount = UsageService.priceFor(version, quantity);
            Money unit = quantity == 0 ? Money.zero(version.currency()) : Money.of(amount.minor() / quantity, version.currency());
            lines.add(new ProposedLine(LineType.USAGE, version.getPlan().getName() + " usage, " + quantity + " units ("
                    + version.getPricingModel().name().toLowerCase() + ")", quantity, unit, amount, from, to, false));
        }
        return lines;
    }

    private void collectIfOpen(Invoice invoice) {
        if (invoice.getStatus() == com.sharvary.billing.invoice.InvoiceStatus.OPEN) {
            payments.collect(invoice.getId(), PaymentService.TRIGGER_INITIAL);
        }
    }

    private static String describe(PlanVersion version, int quantity) {
        String base = version.getPlan().getName();
        return version.getPricingModel() == com.sharvary.billing.plan.PricingModel.PER_SEAT
                ? base + " x " + quantity + " seat" + (quantity == 1 ? "" : "s") : base;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

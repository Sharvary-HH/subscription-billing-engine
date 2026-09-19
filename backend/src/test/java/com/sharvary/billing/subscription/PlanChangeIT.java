package com.sharvary.billing.subscription;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.config.MutableClock;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.customer.CustomerRepository;
import com.sharvary.billing.invoice.CreditNoteService;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.invoice.InvoiceKind;
import com.sharvary.billing.invoice.InvoiceRepository;
import com.sharvary.billing.invoice.InvoiceStatus;
import com.sharvary.billing.invoice.LineType;
import com.sharvary.billing.payment.MockPaymentProvider;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.subscription.proration.ProrationCalculator;
import com.sharvary.billing.support.Fixtures;
import com.sharvary.billing.support.PostgresIT;
import com.sharvary.billing.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2's "done when": three changes in one cycle charge exactly the per-plan durations. */
class PlanChangeIT extends PostgresIT {

    @Autowired Fixtures fixtures;
    @Autowired BillingService billing;
    @Autowired BillingJob job;
    @Autowired InvoiceRepository invoices;
    @Autowired CustomerRepository customers;
    @Autowired CreditNoteService creditNotes;
    @Autowired MutableClock clock;
    @Autowired MockPaymentProvider provider;

    static final LocalDate MAR_1 = LocalDate.of(2026, 3, 1);
    static final LocalDate APR_1 = LocalDate.of(2026, 4, 1);

    @BeforeEach
    void reset() {
        fixtures.wipe();
        provider.reset();
        clock.set(MAR_1.atTime(9, 0).toInstant(ZoneOffset.UTC));
    }

    private void at(LocalDate day) {
        clock.set(day.atTime(9, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    void upgradeThenDowngradeThenUpgradeChargesExactlyThePerPlanDurations() {
        Customer customer = fixtures.customer("US");
        PlanVersion a = fixtures.flatMonthly(1999);
        PlanVersion b = fixtures.flatMonthly(4999);
        PlanVersion c = fixtures.flatMonthly(999);
        Subscription sub = billing.subscribe(customer.getId(), a.getId(), 1, 0, List.of());
        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(APR_1);

        LocalDate d1 = MAR_1.plusDays(10);
        LocalDate d2 = MAR_1.plusDays(18);
        LocalDate d3 = MAR_1.plusDays(25);

        at(d1);
        var up1 = billing.change(sub.getId(), b.getId(), 1);
        assertThat(up1.invoice()).isPresent();
        assertThat(up1.invoice().get().getKind()).isEqualTo(InvoiceKind.PRORATION);
        assertThat(up1.invoice().get().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(up1.invoice().get().getLines()).extracting(l -> l.getType())
                .containsExactly(LineType.PRORATION_CREDIT, LineType.PRORATION_CHARGE);
        assertThat(up1.invoice().get().getLines()).allMatch(l -> l.isProration());

        at(d2);
        var down = billing.change(sub.getId(), c.getId(), 1);
        assertThat(down.invoice()).isEmpty();
        Money balanceAfterDowngrade = customers.findById(customer.getId()).orElseThrow().creditBalance();
        assertThat(balanceAfterDowngrade).isEqualTo(down.credited().minus(down.charged()));
        assertThat(balanceAfterDowngrade.isPositive()).isTrue();

        at(d3);
        var up2 = billing.change(sub.getId(), a.getId(), 1);
        assertThat(up2.invoice()).isPresent();
        Invoice last = up2.invoice().get();
        assertThat(last.getLines()).extracting(l -> l.getType())
                .contains(LineType.CREDIT_BALANCE);

        Money charged = Money.zero(Fixtures.USD);
        for (Invoice i : invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId())) {
            charged = charged.plus(i.total());
        }
        Money leftover = customers.findById(customer.getId()).orElseThrow().creditBalance();
        Money effectivelyCharged = charged.minus(leftover);

        Money expected = ProrationCalculator.amountFor(a.basePrice(), MAR_1, APR_1, MAR_1, d1)
                .plus(ProrationCalculator.amountFor(b.basePrice(), MAR_1, APR_1, d1, d2))
                .plus(ProrationCalculator.amountFor(c.basePrice(), MAR_1, APR_1, d2, d3))
                .plus(ProrationCalculator.amountFor(a.basePrice(), MAR_1, APR_1, d3, APR_1));
        assertThat(effectivelyCharged).isEqualTo(expected);
    }

    @Test
    void downgradeCreditLargerThanTheNextInvoiceCarriesForward() {
        Customer customer = fixtures.customer("US");
        PlanVersion big = fixtures.flatMonthly(30000);
        PlanVersion small = fixtures.flatMonthly(1000);
        Subscription sub = billing.subscribe(customer.getId(), big.getId(), 1, 0, List.of());

        at(MAR_1.plusDays(1));
        var down = billing.change(sub.getId(), small.getId(), 1);
        Money credit = down.credited().minus(down.charged());
        assertThat(credit.compareTo(Money.of(1000, "USD"))).isGreaterThan(0);

        at(APR_1);
        job.runOnce();
        List<Invoice> all = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId());
        Invoice april = all.get(1);
        assertThat(april.total()).isEqualTo(Money.zero(Fixtures.USD));
        assertThat(april.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(april.getLines()).extracting(l -> l.getType()).containsExactly(LineType.PLAN, LineType.CREDIT_BALANCE);
        assertThat(customers.findById(customer.getId()).orElseThrow().creditBalance())
                .isEqualTo(credit.minus(Money.of(1000, "USD")));
        assertThat(provider.chargesMade()).as("nothing to collect on a fully credited invoice").isEqualTo(1);
    }

    @Test
    void seatChangesProrateLikePlanChanges() {
        Customer customer = fixtures.customer("US");
        PlanVersion seat = fixtures.perSeatMonthly(3100); // 31 days in March: 100/day/seat
        Subscription sub = billing.subscribe(customer.getId(), seat.getId(), 2, 0, List.of());
        at(MAR_1.plusDays(21)); // 10 days remaining
        var r = billing.change(sub.getId(), null, 5);
        assertThat(r.credited()).isEqualTo(Money.of(2 * 1000, "USD"));
        assertThat(r.charged()).isEqualTo(Money.of(5 * 1000, "USD"));
        assertThat(r.invoice().get().total()).isEqualTo(Money.of(3000, "USD"));
        assertThat(r.subscription().getQuantity()).isEqualTo(5);
    }

    @Test
    void changingDuringATrialJustSwitchesThePlan() {
        Customer customer = fixtures.customer("US");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(1000).getId(), 1, 10, List.of());
        PlanVersion other = fixtures.flatMonthly(5000);
        var r = billing.change(sub.getId(), other.getId(), 1);
        assertThat(r.invoice()).isEmpty();
        assertThat(r.subscription().getPlanVersion().getId()).isEqualTo(other.getId());
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId())).isEmpty();
    }

    @Test
    void changeRules() {
        Customer customer = fixtures.customer("US");
        PlanVersion a = fixtures.flatMonthly(1000);
        Subscription sub = billing.subscribe(customer.getId(), a.getId(), 1, 0, List.of());
        assertThatThrownBy(() -> billing.change(sub.getId(), a.getId(), 1)).hasMessageContaining("nothing to change");
        assertThatThrownBy(() -> billing.change(sub.getId(), fixtures.tieredMonthly().getId(), 1)).hasMessageContaining("metered");
        assertThatThrownBy(() -> billing.change(sub.getId(), a.getId(), 0)).isInstanceOf(IllegalArgumentException.class);
        billing.cancel(sub.getId(), true);
        assertThatThrownBy(() -> billing.change(sub.getId(), fixtures.flatMonthly(2000).getId(), 1))
                .hasMessageContaining("CANCELED");
        assertThatThrownBy(() -> billing.cancel(sub.getId(), true)).hasMessageContaining("cannot move");
    }

    @Test
    void creditNotesCorrectPaidInvoicesWithoutTouchingThem() {
        Customer customer = fixtures.customer("US");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(5000).getId(), 1, 0, List.of());
        Invoice paid = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId()).get(0);
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThatThrownBy(() -> creditNotes.issue(paid.getId(), "x", List.of())).isInstanceOf(IllegalArgumentException.class);

        var partial = creditNotes.issue(paid.getId(), "goodwill", List.of(new CreditNoteService.LineSpec("Outage credit", 2000)));
        assertThat(partial.total()).isEqualTo(Money.of(2000, "USD"));
        assertThat(partial.getCreditNoteNumber()).startsWith("CN-");
        assertThat(invoices.findById(paid.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoices.findById(paid.getId()).orElseThrow().total()).isEqualTo(Money.of(5000, "USD"));
        assertThat(provider.refundsMade()).isEqualTo(1);

        assertThatThrownBy(() -> creditNotes.issue(paid.getId(), "too much", List.of(new CreditNoteService.LineSpec("x", 3001))))
                .hasMessageContaining("exceeds");

        var rest = creditNotes.issue(paid.getId(), "full refund", List.of(new CreditNoteService.LineSpec("Remainder", 3000)));
        assertThat(rest.getLines()).hasSize(1);
        assertThat(invoices.findById(paid.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.REFUNDED);
        assertThat(creditNotes.forInvoice(paid.getId())).hasSize(2);

        // Open invoices are voided, not credited.
        Customer noCard = fixtures.customerWithCard("tok_decline");
        Subscription open = billing.subscribe(noCard.getId(), fixtures.flatMonthly(100).getId(), 1, 0, List.of());
        Invoice openInvoice = invoices.findBySubscriptionIdOrderByCreatedAtAsc(open.getId()).get(0);
        assertThat(openInvoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThatThrownBy(() -> creditNotes.issue(openInvoice.getId(), "x", List.of(new CreditNoteService.LineSpec("x", 1))))
                .hasMessageContaining("paid invoices");
    }
}

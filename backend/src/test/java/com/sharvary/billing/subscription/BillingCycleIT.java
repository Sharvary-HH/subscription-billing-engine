package com.sharvary.billing.subscription;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.config.MutableClock;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.invoice.InvoiceKind;
import com.sharvary.billing.invoice.InvoiceRepository;
import com.sharvary.billing.invoice.InvoiceStatus;
import com.sharvary.billing.invoice.LineType;
import com.sharvary.billing.payment.MockPaymentProvider;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.support.Fixtures;
import com.sharvary.billing.support.PostgresIT;
import com.sharvary.billing.support.TestClockConfig;
import com.sharvary.billing.usage.UsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 1's "done when": subscribe, advance the clock, see exactly the right invoices. */
class BillingCycleIT extends PostgresIT {

    @Autowired Fixtures fixtures;
    @Autowired BillingService billing;
    @Autowired BillingJob job;
    @Autowired InvoiceRepository invoices;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired MutableClock clock;
    @Autowired MockPaymentProvider provider;
    @Autowired UsageService usage;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        fixtures.wipe();
        provider.reset();
        clock.set(TestClockConfig.START); // 31 January 2026
    }

    @Test
    void flywayAppliedEveryMigrationFromEmpty() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isEqualTo(3);
    }

    @Test
    void monthlySubscriptionAnchoredOnThe31stBillsCorrectlyAcrossFebruary() {
        Customer customer = fixtures.customer("US");
        PlanVersion plan = fixtures.flatMonthly(1999);
        Subscription sub = billing.subscribe(customer.getId(), plan.getId(), 1, 0, List.of());

        assertThat(sub.getAnchorDay()).isEqualTo(31);
        assertThat(sub.getCurrentPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
        List<Invoice> first = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId());
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(first.get(0).total()).isEqualTo(Money.of(1999, "USD"));

        // Nothing due yet.
        clock.set(LocalDate.of(2026, 2, 27).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(job.runOnce()).isZero();

        // Period end passes: one more invoice, period now Feb 28 -> Mar 31 (anchor restored).
        clock.set(LocalDate.of(2026, 2, 28).atTime(6, 0).toInstant(ZoneOffset.UTC));
        assertThat(job.runOnce()).isEqualTo(1);
        Subscription rolled = subscriptions.findById(sub.getId()).orElseThrow();
        assertThat(rolled.getCurrentPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(rolled.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));

        // Running again in the same period does nothing.
        assertThat(job.runOnce()).isZero();

        clock.set(LocalDate.of(2026, 3, 31).atTime(6, 0).toInstant(ZoneOffset.UTC));
        assertThat(job.runOnce()).isEqualTo(1);
        rolled = subscriptions.findById(sub.getId()).orElseThrow();
        assertThat(rolled.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 4, 30));

        List<Invoice> all = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId());
        assertThat(all).hasSize(3);
        assertThat(all).extracting(Invoice::getPeriodStart).containsExactly(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
        assertThat(all).allSatisfy(i -> {
            assertThat(i.getKind()).isEqualTo(InvoiceKind.RECURRING);
            assertThat(i.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(i.total()).isEqualTo(Money.of(1999, "USD"));
            assertThat(i.getLines()).hasSize(1);
            assertThat(i.getLines().get(0).getType()).isEqualTo(LineType.PLAN);
        });
        assertThat(provider.chargesMade()).isEqualTo(3);
    }

    @Test
    void aClockJumpOfSeveralMonthsProducesOneInvoicePerMissedPeriod() {
        Customer customer = fixtures.customer("US");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of());
        clock.set(LocalDate.of(2026, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(job.runOnce()).isEqualTo(1);
        List<Invoice> all = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId());
        // Jan31, Feb28, Mar31, Apr30, May31 -> five periods started on or before 15 June.
        assertThat(all).extracting(Invoice::getPeriodStart).containsExactly(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31));
        assertThat(all).extracting(Invoice::getPeriodStart).doesNotHaveDuplicates();
    }

    @Test
    void taxIsChargedByRegionAndPerSeatPricesScale() {
        Customer customer = fixtures.customer("IN"); // 18%
        Subscription sub = billing.subscribe(customer.getId(), fixtures.perSeatMonthly(1200).getId(), 3, 0, List.of());
        Invoice invoice = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId()).get(0);
        assertThat(invoice.subtotal()).isEqualTo(Money.of(3600, "USD"));
        assertThat(invoice.tax()).isEqualTo(Money.of(648, "USD"));
        assertThat(invoice.total()).isEqualTo(Money.of(4248, "USD"));
        assertThat(invoice.getLines().get(0).getQuantity()).isEqualTo(3);
        assertThat(invoice.getLines().get(0).unitPrice()).isEqualTo(Money.of(1200, "USD"));
    }

    @Test
    void trialConvertsToPaidAtTrialEnd() {
        Customer customer = fixtures.customer("US");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(2500).getId(), 1, 14, List.of());
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(sub.getTrialEnd()).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId())).isEmpty();

        clock.set(LocalDate.of(2026, 2, 14).atTime(1, 0).toInstant(ZoneOffset.UTC));
        job.runOnce();
        Subscription active = subscriptions.findById(sub.getId()).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(active.getTrialEnd()).isNull();
        assertThat(active.getCurrentPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(active.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 31)); // anchor stays 31
        List<Invoice> all = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId());
        assertThat(all).hasSize(1);
        assertThat(all.get(0).total()).isEqualTo(Money.of(2500, "USD"));
    }

    @Test
    void trialCanceledBeforeItEndsIsNeverBilled() {
        Customer customer = fixtures.customer("US");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(2500).getId(), 1, 14, List.of());
        billing.cancel(sub.getId(), true);
        clock.advance(Duration.ofDays(60));
        job.runOnce();
        assertThat(subscriptions.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId())).isEmpty();
    }

    @Test
    void cancelAtPeriodEndStopsBillingAfterTheCurrentPeriod() {
        Customer customer = fixtures.customer("US");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of());
        Subscription scheduled = billing.cancel(sub.getId(), false);
        assertThat(scheduled.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(scheduled.getCancelAt()).isEqualTo(LocalDate.of(2026, 2, 28));

        clock.set(LocalDate.of(2026, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        job.runOnce();
        Subscription after = subscriptions.findById(sub.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(after.getCanceledAt()).isNotNull();
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId())).hasSize(1);

        // Undoing a scheduled cancel is possible before it fires.
        Subscription other = billing.subscribe(customer.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of());
        billing.cancel(other.getId(), false);
        assertThat(billing.undoScheduledCancel(other.getId()).getCancelAt()).isNull();
    }

    @Test
    void meteredUsageIsBilledInArrearsOnTheNextInvoice() {
        Customer customer = fixtures.customer("US");
        PlanVersion tiered = fixtures.tieredMonthly(); // 0-100 @10c, then 5c
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of(tiered.getId()));
        var item = sub.getItems().get(0);
        clock.advance(Duration.ofDays(3));
        usage.record(item.getId(), 80, clock.instant(), "u1");
        clock.advance(Duration.ofDays(7));
        usage.record(item.getId(), 70, clock.instant(), "u2");
        // Replaying a key does not add usage.
        usage.record(item.getId(), 70, clock.instant(), "u2");

        clock.set(LocalDate.of(2026, 2, 28).atTime(6, 0).toInstant(ZoneOffset.UTC));
        job.runOnce();
        List<Invoice> all = invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId());
        Invoice second = all.get(1);
        assertThat(second.getLines()).hasSize(2);
        var usageLine = second.getLines().get(1);
        assertThat(usageLine.getType()).isEqualTo(LineType.USAGE);
        assertThat(usageLine.getQuantity()).isEqualTo(150);
        assertThat(usageLine.amount()).isEqualTo(Money.of(100 * 10 + 50 * 5, "USD"));
        assertThat(usageLine.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(usageLine.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(second.total()).isEqualTo(Money.of(1000 + 1250, "USD"));

        // Usage stamped into the now-closed period is refused.
        Instant closed = LocalDate.of(2026, 2, 10).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThatThrownBy(() -> usage.record(item.getId(), 1, closed, "u3"))
                .hasMessageContaining("closed billing period");
        assertThatThrownBy(() -> usage.record(item.getId(), 1, clock.instant().plus(Duration.ofDays(1)), "u4"))
                .hasMessageContaining("future");
        assertThatThrownBy(() -> usage.record(item.getId(), 0, clock.instant(), "u5")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void estimateShowsTheNextInvoiceWithoutCreatingIt() {
        Customer customer = fixtures.customer("GB");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of());
        var estimate = billing.estimateNextInvoice(sub.getId());
        assertThat(estimate.subtotal()).isEqualTo(Money.of(1000, "USD"));
        assertThat(estimate.tax()).isEqualTo(Money.of(200, "USD"));
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId())).hasSize(1);
        billing.cancel(sub.getId(), false);
        assertThat(billing.estimateNextInvoice(sub.getId()).lines()).isEmpty();
    }

    @Test
    void pauseStopsBillingAndResumeStartsAFreshPeriodReanchoredToToday() {
        Customer customer = fixtures.customer("US");
        Subscription sub = billing.subscribe(customer.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of());
        billing.pause(sub.getId());
        clock.set(LocalDate.of(2026, 4, 10).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(job.runOnce()).isZero();
        Subscription resumed = billing.resume(sub.getId());
        assertThat(resumed.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(resumed.getCurrentPeriodStart()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(resumed.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(resumed.getAnchorDay()).isEqualTo(10);
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub.getId())).hasSize(2);
    }

    @Test
    void subscriptionRulesAreEnforced() {
        Customer customer = fixtures.customer("US");
        PlanVersion metered = fixtures.tieredMonthly();
        assertThatThrownBy(() -> billing.subscribe(customer.getId(), metered.getId(), 1, 0, List.of()))
                .hasMessageContaining("metered");
        assertThatThrownBy(() -> billing.subscribe(customer.getId(), fixtures.flatMonthly(1).getId(), 0, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        PlanVersion flat = fixtures.flatMonthly(1);
        assertThatThrownBy(() -> billing.subscribe(customer.getId(), flat.getId(), 1, 0, List.of(flat.getId())))
                .hasMessageContaining("not metered");
    }
}

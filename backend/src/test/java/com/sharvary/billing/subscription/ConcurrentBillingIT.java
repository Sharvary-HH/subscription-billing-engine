package com.sharvary.billing.subscription;

import com.sharvary.billing.config.MutableClock;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.invoice.InvoiceRepository;
import com.sharvary.billing.payment.MockPaymentProvider;
import com.sharvary.billing.support.Fixtures;
import com.sharvary.billing.support.PostgresIT;
import com.sharvary.billing.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The test that matters most. Two copies of the billing job run at the same instant against the
 * same due subscriptions; afterwards each subscription has exactly one invoice for the period.
 */
class ConcurrentBillingIT extends PostgresIT {

    @Autowired Fixtures fixtures;
    @Autowired BillingService billing;
    @Autowired BillingJob job;
    @Autowired InvoiceRepository invoices;
    @Autowired MutableClock clock;
    @Autowired MockPaymentProvider provider;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        fixtures.wipe();
        provider.reset();
        clock.set(TestClockConfig.START);
    }

    @Test
    void twoJobInstancesRunningTogetherProduceOneInvoicePerSubscription() throws Exception {
        List<UUID> subs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Customer c = fixtures.customer("US");
            subs.add(billing.subscribe(c.getId(), fixtures.flatMonthly(1000 + i).getId(), 1, 0, List.of()).getId());
        }
        clock.set(LocalDate.of(2026, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant());

        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return job.runOnce();
            }));
        }
        go.countDown();
        int processed = 0;
        for (Future<Integer> f : results) {
            processed += f.get();
        }
        pool.shutdown();

        assertThat(processed).isEqualTo(20);
        for (UUID id : subs) {
            var forSub = invoices.findBySubscriptionIdOrderByCreatedAtAsc(id);
            assertThat(forSub).as("subscription %s", id).hasSize(2);
            assertThat(forSub.get(1).getPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 28));
        }
        Integer duplicates = jdbc.queryForObject("""
                select count(*) from (select subscription_id, period_start from invoices
                where kind = 'RECURRING' group by 1, 2 having count(*) > 1) d
                """, Integer.class);
        assertThat(duplicates).isZero();
        assertThat(provider.chargesMade()).isEqualTo(40);
    }

    @Test
    void theUniqueIndexRejectsADuplicateRecurringInvoiceEvenIfTheApplicationTries() {
        Customer c = fixtures.customer("US");
        UUID sub = billing.subscribe(c.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of()).getId();
        assertThatThrownBy(() -> jdbc.update("""
                insert into invoices (id, invoice_number, customer_id, subscription_id, status, kind, currency,
                    period_start, period_end, created_at)
                values (?, 'INV-DUP', ?, ?, 'OPEN', 'RECURRING', 'USD', ?, ?, now())
                """, UUID.randomUUID(), c.getId(), sub, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_invoices_subscription_period");

        // A proration invoice on the same start date is fine: the index is partial.
        jdbc.update("""
                insert into invoices (id, invoice_number, customer_id, subscription_id, status, kind, currency,
                    period_start, period_end, created_at)
                values (?, 'INV-PRO', ?, ?, 'OPEN', 'PRORATION', 'USD', ?, ?, now())
                """, UUID.randomUUID(), c.getId(), sub, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28));
    }

    @Test
    void aFailureMidRolloverLeavesNothingBehind() {
        Customer c = fixtures.customer("US");
        UUID sub = billing.subscribe(c.getId(), fixtures.flatMonthly(1000).getId(), 1, 0, List.of()).getId();
        clock.set(LocalDate.of(2026, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant());

        // Break the payment path: no default card leaves a declined attempt, not an exception, so
        // break the invoice numbering instead, which throws from inside the same transaction.
        jdbc.execute("alter sequence invoice_number_seq rename to invoice_number_seq_hidden");
        try {
            assertThat(job.runOnce()).isEqualTo(1); // claimed, failed, rolled back
        } finally {
            jdbc.execute("alter sequence invoice_number_seq_hidden rename to invoice_number_seq");
        }
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub)).hasSize(1);
        LocalDate periodEnd = jdbc.queryForObject("select current_period_end from subscriptions where id = ?", LocalDate.class, sub);
        assertThat(periodEnd).isEqualTo(LocalDate.of(2026, 2, 28));

        // With the sequence back the next run completes normally.
        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(invoices.findBySubscriptionIdOrderByCreatedAtAsc(sub)).hasSize(2);
    }
}

package com.sharvary.billing.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The recurring billing run. Produces exactly one invoice per subscription per period, even when
 * two copies of this job run at the same moment on two instances.
 *
 * <p>Three layers make that true, and they are deliberately redundant:
 * <ol>
 *   <li>Each subscription is claimed with {@code SELECT ... FOR UPDATE SKIP LOCKED} in its own
 *       short transaction. A second instance skips whatever this one holds.</li>
 *   <li>Rolling the period and inserting the invoice commit together. If either fails, neither
 *       happened, and the subscription is still due for the next run.</li>
 *   <li>The partial unique index on {@code invoices (subscription_id, period_start)} rejects a
 *       duplicate recurring invoice at commit no matter what the application thought it knew.</li>
 * </ol>
 */
@Component
public class BillingJob {

    private static final Logger log = LoggerFactory.getLogger(BillingJob.class);
    private static final UUID NONE = new UUID(0, 0);

    private final SubscriptionRepository subscriptions;
    private final BillingService billing;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final int batchSize;

    public BillingJob(SubscriptionRepository subscriptions, BillingService billing, PlatformTransactionManager txManager,
                      Clock clock, @Value("${billing.jobs.batch-size:50}") int batchSize) {
        this.subscriptions = subscriptions;
        this.billing = billing;
        this.transaction = new TransactionTemplate(txManager);
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${billing.jobs.billing-cron:0 */5 * * * *}")
    public void scheduled() {
        runOnce();
    }

    /** Bills everything that is due. Returns how many subscriptions were processed. */
    public int runOnce() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<UUID> skip = new ArrayList<>(List.of(NONE));
        int processed = 0;
        for (int i = 0; i < batchSize; i++) {
            Optional<UUID> claimed = transaction.execute(status -> {
                Optional<Subscription> due = subscriptions.claimNextDue(today, skip);
                if (due.isEmpty()) {
                    return Optional.empty();
                }
                Subscription subscription = due.get();
                try {
                    billing.rollForward(subscription);
                } catch (DataIntegrityViolationException e) {
                    log.warn("invoice for subscription {} already exists; the unique index caught a duplicate run",
                            subscription.getId());
                    status.setRollbackOnly();
                    skip.add(subscription.getId());
                } catch (RuntimeException e) {
                    log.error("billing subscription {} failed", subscription.getId(), e);
                    status.setRollbackOnly();
                    skip.add(subscription.getId());
                }
                return Optional.of(subscription.getId());
            });
            if (claimed == null || claimed.isEmpty()) {
                break;
            }
            processed++;
        }
        return processed;
    }
}

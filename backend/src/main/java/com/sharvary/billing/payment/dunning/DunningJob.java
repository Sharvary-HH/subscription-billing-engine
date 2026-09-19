package com.sharvary.billing.payment.dunning;

import com.sharvary.billing.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fires retries that have come due. Each case is claimed with {@code FOR UPDATE SKIP LOCKED} in
 * its own transaction, so two instances of this job split the queue rather than doubling it.
 */
@Component
public class DunningJob {

    private static final Logger log = LoggerFactory.getLogger(DunningJob.class);
    private static final UUID NONE = new UUID(0, 0);

    private final DunningCaseRepository cases;
    private final PaymentService payments;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final int batchSize;

    public DunningJob(DunningCaseRepository cases, PaymentService payments,
                      org.springframework.transaction.PlatformTransactionManager txManager, Clock clock,
                      @Value("${billing.jobs.batch-size:50}") int batchSize) {
        this.cases = cases;
        this.payments = payments;
        this.transaction = new TransactionTemplate(txManager);
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${billing.jobs.dunning-cron:0 */5 * * * *}")
    public void scheduled() {
        runOnce();
    }

    /** Processes everything that is due right now. Returns how many retries fired. */
    public int runOnce() {
        List<UUID> skip = new ArrayList<>(List.of(NONE));
        int fired = 0;
        for (int i = 0; i < batchSize; i++) {
            Optional<UUID> processed = transaction.execute(status -> {
                Optional<DunningCase> claimed = cases.claimNextDue(clock.instant(), skip);
                if (claimed.isEmpty()) {
                    return Optional.empty();
                }
                DunningCase c = claimed.get();
                try {
                    payments.collect(c.getInvoice().getId(), PaymentService.TRIGGER_RETRY);
                } catch (RuntimeException e) {
                    log.error("dunning retry for invoice {} failed", c.getInvoice().getId(), e);
                    status.setRollbackOnly();
                    skip.add(c.getId());
                }
                return Optional.of(c.getId());
            });
            if (processed == null || processed.isEmpty()) {
                break;
            }
            fired++;
        }
        return fired;
    }

    /**
     * Immediate retry after a card update, run once the update has committed. It needs its own
     * transaction: an after-commit listener that merely joins the (already committed) transaction
     * would do its work and never flush it.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetryRequested(DunningService.RetryRequestedEvent event) {
        try {
            TransactionTemplate fresh = new TransactionTemplate(transaction.getTransactionManager());
            fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            fresh.executeWithoutResult(status -> payments.collect(event.invoiceId(), PaymentService.TRIGGER_CARD_UPDATED));
        } catch (RuntimeException e) {
            log.error("immediate retry for invoice {} failed", event.invoiceId(), e);
        }
    }
}

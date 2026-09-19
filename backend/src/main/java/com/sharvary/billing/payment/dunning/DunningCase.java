package com.sharvary.billing.payment.dunning;

import com.sharvary.billing.common.IllegalStateTransitionException;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.subscription.Subscription;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted state of one failed-payment recovery sequence. The whole sequence is described by
 * (state, retries_done, started_at): the next retry time is a function of those, and there is no
 * flag anywhere that could disagree with them.
 */
@Entity
@Table(name = "dunning_cases")
public class DunningCase {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DunningState state;

    @Column(name = "retries_done", nullable = false)
    private int retriesDone;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private long version;

    protected DunningCase() {
    }

    public DunningCase(UUID id, Invoice invoice, Subscription subscription, Instant startedAt, Instant firstRetryAt) {
        this.id = id;
        this.invoice = invoice;
        this.subscription = subscription;
        this.state = DunningState.RETRYING;
        this.retriesDone = 0;
        this.startedAt = startedAt;
        this.nextRetryAt = firstRetryAt;
    }

    /** A retry ran and failed. Either schedules the next one or reports the sequence is spent. */
    public boolean recordFailedRetry(RetrySchedule schedule) {
        requireRetrying();
        retriesDone++;
        var next = schedule.nextRetryAt(startedAt, retriesDone);
        if (next.isPresent()) {
            nextRetryAt = next.get();
            return true;
        }
        nextRetryAt = null;
        return false;
    }

    /** The customer fixed their card. Start the week over, and retry right now. */
    public void restart(Instant now) {
        requireRetrying();
        retriesDone = 0;
        startedAt = now;
        nextRetryAt = now;
    }

    public void resolve(DunningState terminal, Instant at) {
        transitionTo(terminal);
        this.nextRetryAt = null;
        this.resolvedAt = at;
    }

    public void transitionTo(DunningState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateTransitionException("dunning case " + id, state, next);
        }
        this.state = next;
    }

    private void requireRetrying() {
        if (state != DunningState.RETRYING) {
            throw new IllegalStateException("dunning case " + id + " is " + state);
        }
    }

    public UUID getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public DunningState getState() {
        return state;
    }

    public int getRetriesDone() {
        return retriesDone;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}

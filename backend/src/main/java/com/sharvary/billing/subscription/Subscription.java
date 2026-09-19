package com.sharvary.billing.subscription;

import com.sharvary.billing.common.IllegalStateTransitionException;
import com.sharvary.billing.common.Money;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.plan.PlanVersion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_version_id")
    private PlanVersion planVersion;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "anchor_day", nullable = false)
    private int anchorDay;

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDate currentPeriodEnd;

    @Column(name = "trial_end")
    private LocalDate trialEnd;

    @Column(name = "cancel_at")
    private LocalDate cancelAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt asc")
    private List<SubscriptionItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Subscription() {
    }

    public Subscription(UUID id, Customer customer, PlanVersion planVersion, int quantity, SubscriptionStatus status,
                        int anchorDay, LocalDate periodStart, LocalDate periodEnd, LocalDate trialEnd, Instant createdAt) {
        this.id = id;
        this.customer = customer;
        this.planVersion = planVersion;
        this.quantity = quantity;
        this.status = status;
        this.anchorDay = anchorDay;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.trialEnd = trialEnd;
        this.createdAt = createdAt;
    }

    /** The one place a status changes. Illegal moves throw; nothing else is touched. */
    public void transitionTo(SubscriptionStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException("subscription " + id, status, next);
        }
        this.status = next;
    }

    public void cancelNow(Instant at, LocalDate today) {
        transitionTo(SubscriptionStatus.CANCELED);
        this.canceledAt = at;
        this.cancelAt = today;
    }

    public void cancelAtPeriodEnd() {
        if (status == SubscriptionStatus.CANCELED) {
            throw new IllegalStateTransitionException("subscription " + id, status, SubscriptionStatus.CANCELED);
        }
        this.cancelAt = currentPeriodEnd;
    }

    public void clearScheduledCancel() {
        this.cancelAt = null;
    }

    public boolean isScheduledToCancelAtPeriodEnd() {
        return cancelAt != null && !cancelAt.isAfter(currentPeriodEnd);
    }

    public void switchTo(PlanVersion newVersion, int newQuantity) {
        this.planVersion = newVersion;
        this.quantity = newQuantity;
    }

    /** A resume after a lapse starts over from today: new anchor, new period. */
    public void restartOn(LocalDate newStart, LocalDate newEnd) {
        this.anchorDay = newStart.getDayOfMonth();
        rollPeriod(newStart, newEnd);
    }

    public void rollPeriod(LocalDate newStart, LocalDate newEnd) {
        if (!newEnd.isAfter(newStart)) {
            throw new IllegalArgumentException("period end must be after start");
        }
        this.currentPeriodStart = newStart;
        this.currentPeriodEnd = newEnd;
    }

    public void endTrial() {
        transitionTo(SubscriptionStatus.ACTIVE);
        this.trialEnd = null;
    }

    public SubscriptionItem addItem(UUID itemId, PlanVersion addOn, int qty, Instant at) {
        SubscriptionItem item = new SubscriptionItem(itemId, this, addOn, qty, at);
        items.add(item);
        return item;
    }

    /** Full-period price of the base plan at the current quantity. */
    public Money periodPrice() {
        return planVersion.periodPrice(quantity);
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public PlanVersion getPlanVersion() {
        return planVersion;
    }

    public int getQuantity() {
        return quantity;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public int getAnchorDay() {
        return anchorDay;
    }

    public LocalDate getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public LocalDate getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public LocalDate getTrialEnd() {
        return trialEnd;
    }

    public LocalDate getCancelAt() {
        return cancelAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public List<SubscriptionItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

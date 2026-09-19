package com.sharvary.billing.subscription;

import com.sharvary.billing.plan.PlanVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A metered add-on line on a subscription. Usage records point at one of these. */
@Entity
@Table(name = "subscription_items")
public class SubscriptionItem {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_version_id")
    private PlanVersion planVersion;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubscriptionItem() {
    }

    SubscriptionItem(UUID id, Subscription subscription, PlanVersion planVersion, int quantity, Instant createdAt) {
        this.id = id;
        this.subscription = subscription;
        this.planVersion = planVersion;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public PlanVersion getPlanVersion() {
        return planVersion;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

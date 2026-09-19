package com.sharvary.billing.usage;

import com.sharvary.billing.subscription.SubscriptionItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only. No setters, no update path, no delete endpoint. */
@Entity
@Table(name = "usage_records")
public class UsageRecord {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_item_id")
    private SubscriptionItem item;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UsageRecord() {
    }

    public UsageRecord(UUID id, SubscriptionItem item, long quantity, Instant recordedAt, String idempotencyKey,
                       Instant createdAt) {
        this.id = id;
        this.item = item;
        this.quantity = quantity;
        this.recordedAt = recordedAt;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public SubscriptionItem getItem() {
        return item;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.sharvary.billing.payment;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.invoice.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    public enum Status {
        SUCCEEDED,
        FAILED,
        TIMED_OUT
    }

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID id, Invoice invoice, int attemptNumber, String idempotencyKey, Money amount,
                          Status status, String providerRef, String failureReason, String triggeredBy, Instant createdAt) {
        this.id = id;
        this.invoice = invoice;
        this.attemptNumber = attemptNumber;
        this.idempotencyKey = idempotencyKey;
        this.amountMinor = amount.minor();
        this.currency = amount.currencyCode();
        this.status = status;
        this.providerRef = providerRef;
        this.failureReason = failureReason;
        this.triggeredBy = triggeredBy;
        this.createdAt = createdAt;
    }

    public Money amount() {
        return Money.of(amountMinor, currency);
    }

    public UUID getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Status getStatus() {
        return status;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

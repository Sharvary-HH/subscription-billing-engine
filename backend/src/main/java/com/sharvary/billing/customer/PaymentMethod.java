package com.sharvary.billing.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A tokenised card. We never see a PAN; the provider hands back a token and we keep the brand and
 * last four digits for display.
 */
@Entity
@Table(name = "payment_methods")
public class PaymentMethod {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "provider_token", nullable = false)
    private String providerToken;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false, length = 4)
    private String last4;

    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentMethod() {
    }

    public PaymentMethod(UUID id, Customer customer, String providerToken, String brand, String last4,
                         boolean defaultMethod, Instant createdAt) {
        this.id = id;
        this.customer = customer;
        this.providerToken = providerToken;
        this.brand = brand;
        this.last4 = last4;
        this.defaultMethod = defaultMethod;
        this.createdAt = createdAt;
    }

    void clearDefault() {
        this.defaultMethod = false;
    }

    void markDefault() {
        this.defaultMethod = true;
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getProviderToken() {
        return providerToken;
    }

    public String getBrand() {
        return brand;
    }

    public String getLast4() {
        return last4;
    }

    public boolean isDefaultMethod() {
        return defaultMethod;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

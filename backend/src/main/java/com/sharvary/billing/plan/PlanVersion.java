package com.sharvary.billing.plan;

import com.sharvary.billing.common.Money;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * A priced snapshot of a plan. Immutable after creation: a price change is a new version, and
 * existing subscriptions keep pointing at the version they signed up on.
 */
@Entity
@Table(name = "plan_versions")
public class PlanVersion {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false)
    private BillingInterval billingInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false)
    private PricingModel pricingModel;

    @Column(name = "base_price_minor", nullable = false)
    private long basePriceMinor;

    @OneToMany(mappedBy = "planVersion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("tierIndex asc")
    private List<PriceTier> tiers = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlanVersion() {
    }

    public PlanVersion(UUID id, Plan plan, int version, Currency currency, BillingInterval billingInterval,
                       PricingModel pricingModel, long basePriceMinor, Instant createdAt) {
        this.id = id;
        this.plan = plan;
        this.version = version;
        this.currency = currency.getCurrencyCode();
        this.billingInterval = billingInterval;
        this.pricingModel = pricingModel;
        this.basePriceMinor = basePriceMinor;
        this.createdAt = createdAt;
    }

    public void addTier(Long upTo, long unitPriceMinor, long flatFeeMinor) {
        tiers.add(new PriceTier(UUID.randomUUID(), this, tiers.size(), upTo, unitPriceMinor, flatFeeMinor));
    }

    public Money basePrice() {
        return Money.of(basePriceMinor, currency);
    }

    /** Price for one full billing period at the given seat count. Metered models have no advance price. */
    public Money periodPrice(int quantity) {
        return switch (pricingModel) {
            case FLAT -> basePrice();
            case PER_SEAT -> basePrice().times(quantity);
            case TIERED, VOLUME -> Money.zero(currency());
        };
    }

    public Currency currency() {
        return Currency.getInstance(currency);
    }

    public String displayName() {
        return plan.getName() + " v" + version;
    }

    public UUID getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getVersion() {
        return version;
    }

    public BillingInterval getBillingInterval() {
        return billingInterval;
    }

    public PricingModel getPricingModel() {
        return pricingModel;
    }

    public List<PriceTier> getTiers() {
        return List.copyOf(tiers);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

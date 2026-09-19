package com.sharvary.billing.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** One band of a tiered or volume price. {@code upTo == null} is the open-ended top band. */
@Entity
@Table(name = "price_tiers")
public class PriceTier {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plan_version_id")
    private PlanVersion planVersion;

    @Column(name = "tier_index", nullable = false)
    private int tierIndex;

    @Column(name = "up_to")
    private Long upTo;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "flat_fee_minor", nullable = false)
    private long flatFeeMinor;

    protected PriceTier() {
    }

    PriceTier(UUID id, PlanVersion planVersion, int tierIndex, Long upTo, long unitPriceMinor, long flatFeeMinor) {
        this.id = id;
        this.planVersion = planVersion;
        this.tierIndex = tierIndex;
        this.upTo = upTo;
        this.unitPriceMinor = unitPriceMinor;
        this.flatFeeMinor = flatFeeMinor;
    }

    public Band toBand() {
        return new Band(upTo, unitPriceMinor, flatFeeMinor);
    }

    public int getTierIndex() {
        return tierIndex;
    }

    public Long getUpTo() {
        return upTo;
    }

    public long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    public long getFlatFeeMinor() {
        return flatFeeMinor;
    }

    /** Plain value used by the pricing functions so they do not depend on JPA. */
    public record Band(Long upTo, long unitPriceMinor, long flatFeeMinor) {
    }
}

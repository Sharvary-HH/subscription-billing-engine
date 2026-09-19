package com.sharvary.billing.plan;

import com.sharvary.billing.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
public class PlanService {

    private final PlanRepository plans;
    private final PlanVersionRepository versions;
    private final Clock clock;

    public PlanService(PlanRepository plans, PlanVersionRepository versions, Clock clock) {
        this.plans = plans;
        this.versions = versions;
        this.clock = clock;
    }

    public record TierSpec(Long upTo, long unitPriceMinor, long flatFeeMinor) {
    }

    public record PriceSpec(Currency currency, BillingInterval interval, PricingModel model,
                            long basePriceMinor, List<TierSpec> tiers) {
    }

    @Transactional
    public PlanVersion createPlan(String code, String name, PriceSpec price) {
        plans.findByCode(code).ifPresent(p -> {
            throw new IllegalArgumentException("plan code " + code + " already exists");
        });
        Plan plan = plans.save(new Plan(UUID.randomUUID(), code, name, clock.instant()));
        return addVersion(plan, 1, price);
    }

    /** A price change: the old version stays untouched, existing subscriptions stay on it. */
    @Transactional
    public PlanVersion publishNewVersion(UUID planId, PriceSpec price) {
        Plan plan = getPlan(planId);
        int next = versions.findFirstByPlanIdOrderByVersionDesc(planId).map(v -> v.getVersion() + 1).orElse(1);
        return addVersion(plan, next, price);
    }

    @Transactional
    public void retire(UUID planId) {
        getPlan(planId).retire();
    }

    @Transactional(readOnly = true)
    public Plan getPlan(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("plan", id));
    }

    @Transactional(readOnly = true)
    public PlanVersion getVersion(UUID versionId) {
        return versions.findById(versionId).orElseThrow(() -> new NotFoundException("plan version", versionId));
    }

    @Transactional(readOnly = true)
    public PlanVersion currentVersion(UUID planId) {
        return versions.findFirstByPlanIdOrderByVersionDesc(planId)
                .orElseThrow(() -> new NotFoundException("plan", planId));
    }

    @Transactional(readOnly = true)
    public List<Plan> listPlans() {
        return plans.findAll();
    }

    @Transactional(readOnly = true)
    public List<PlanVersion> versionsOf(UUID planId) {
        getPlan(planId);
        return versions.findByPlanIdOrderByVersionDesc(planId);
    }

    private PlanVersion addVersion(Plan plan, int number, PriceSpec price) {
        validate(price);
        PlanVersion version = new PlanVersion(UUID.randomUUID(), plan, number, price.currency(), price.interval(),
                price.model(), price.basePriceMinor(), clock.instant());
        if (price.tiers() != null) {
            for (TierSpec tier : price.tiers()) {
                version.addTier(tier.upTo(), tier.unitPriceMinor(), tier.flatFeeMinor());
            }
        }
        return versions.save(version);
    }

    private static void validate(PriceSpec price) {
        if (price.basePriceMinor() < 0) {
            throw new IllegalArgumentException("base price cannot be negative");
        }
        boolean hasTiers = price.tiers() != null && !price.tiers().isEmpty();
        if (price.model().isMetered() && !hasTiers) {
            throw new IllegalArgumentException(price.model() + " pricing needs at least one tier");
        }
        if (!price.model().isMetered() && hasTiers) {
            throw new IllegalArgumentException(price.model() + " pricing does not take tiers");
        }
        if (hasTiers) {
            Long previous = 0L;
            List<TierSpec> tiers = price.tiers();
            for (int i = 0; i < tiers.size(); i++) {
                TierSpec t = tiers.get(i);
                boolean last = i == tiers.size() - 1;
                if (t.upTo() == null && !last) {
                    throw new IllegalArgumentException("only the last tier may be open-ended");
                }
                if (t.upTo() != null && t.upTo() <= previous) {
                    throw new IllegalArgumentException("tier bounds must strictly increase");
                }
                if (t.unitPriceMinor() < 0 || t.flatFeeMinor() < 0) {
                    throw new IllegalArgumentException("tier prices cannot be negative");
                }
                if (t.upTo() != null) {
                    previous = t.upTo();
                }
            }
        }
    }
}

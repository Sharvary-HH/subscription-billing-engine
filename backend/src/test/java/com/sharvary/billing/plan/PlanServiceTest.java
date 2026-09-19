package com.sharvary.billing.plan;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanServiceTest {

    static final Currency USD = Currency.getInstance("USD");
    PlanRepository plans = mock(PlanRepository.class);
    PlanVersionRepository versions = mock(PlanVersionRepository.class);
    PlanService service = new PlanService(plans, versions, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    PlanServiceTest() {
        when(plans.save(any())).thenAnswer(a -> a.getArgument(0));
        when(versions.save(any())).thenAnswer(a -> a.getArgument(0));
        when(plans.findByCode(any())).thenReturn(Optional.empty());
    }

    @Test
    void createsVersionOne() {
        PlanVersion v = service.createPlan("pro", "Pro", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.FLAT, 2900, List.of()));
        assertThat(v.getVersion()).isEqualTo(1);
        assertThat(v.basePrice().minor()).isEqualTo(2900);
        assertThat(v.periodPrice(5).minor()).isEqualTo(2900);
        assertThat(v.displayName()).isEqualTo("Pro v1");
        assertThat(v.getPlan().isActive()).isTrue();
    }

    @Test
    void perSeatPriceScales() {
        PlanVersion v = service.createPlan("team", "Team", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.PER_SEAT, 1200, List.of()));
        assertThat(v.periodPrice(3).minor()).isEqualTo(3600);
    }

    @Test
    void newVersionIncrementsAndLeavesOldUntouched() {
        Plan plan = new Plan(UUID.randomUUID(), "pro", "Pro", Instant.EPOCH);
        PlanVersion v1 = new PlanVersion(UUID.randomUUID(), plan, 1, USD, BillingInterval.MONTHLY, PricingModel.FLAT, 2900, Instant.EPOCH);
        when(plans.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(versions.findFirstByPlanIdOrderByVersionDesc(plan.getId())).thenReturn(Optional.of(v1));

        PlanVersion v2 = service.publishNewVersion(plan.getId(), new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.FLAT, 3900, List.of()));
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v1.basePrice().minor()).isEqualTo(2900);
        assertThat(v2.basePrice().minor()).isEqualTo(3900);
    }

    @Test
    void meteredPlansNeedTiersAndFlatPlansMustNotHaveThem() {
        assertThatThrownBy(() -> service.createPlan("m", "M", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.TIERED, 0, List.of()))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tier");
        assertThatThrownBy(() -> service.createPlan("f", "F", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.FLAT, 100, List.of(new PlanService.TierSpec(null, 1, 0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tierBoundsMustIncreaseAndOnlyTheLastMayBeOpen() {
        assertThatThrownBy(() -> service.createPlan("m", "M", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.TIERED, 0, List.of(new PlanService.TierSpec(100L, 1, 0), new PlanService.TierSpec(50L, 1, 0)))))
                .hasMessageContaining("increase");
        assertThatThrownBy(() -> service.createPlan("m", "M", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.TIERED, 0, List.of(new PlanService.TierSpec(null, 1, 0), new PlanService.TierSpec(50L, 1, 0)))))
                .hasMessageContaining("open-ended");
        assertThatThrownBy(() -> service.createPlan("m", "M", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.TIERED, 0, List.of(new PlanService.TierSpec(100L, -1, 0)))))
                .hasMessageContaining("negative");
        assertThatThrownBy(() -> service.createPlan("m", "M", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.FLAT, -1, List.of()))).hasMessageContaining("negative");
    }

    @Test
    void validTiersAreKeptInOrder() {
        PlanVersion v = service.createPlan("m", "M", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.VOLUME, 0, List.of(new PlanService.TierSpec(100L, 5, 0), new PlanService.TierSpec(null, 3, 100))));
        assertThat(v.getTiers()).hasSize(2);
        assertThat(v.getTiers().get(1).getUpTo()).isNull();
        assertThat(v.getTiers().get(1).getFlatFeeMinor()).isEqualTo(100);
        assertThat(v.periodPrice(1).isZero()).isTrue();
    }

    @Test
    void duplicateCodeIsRejected() {
        when(plans.findByCode("pro")).thenReturn(Optional.of(new Plan(UUID.randomUUID(), "pro", "Pro", Instant.EPOCH)));
        assertThatThrownBy(() -> service.createPlan("pro", "Pro", new PlanService.PriceSpec(USD, BillingInterval.MONTHLY,
                PricingModel.FLAT, 1, List.of()))).hasMessageContaining("already exists");
    }
}

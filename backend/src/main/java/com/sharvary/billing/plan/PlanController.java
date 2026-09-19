package com.sharvary.billing.plan;

import com.sharvary.billing.common.dto.Dtos.PlanDto;
import com.sharvary.billing.common.dto.Dtos.PlanVersionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    public record TierRequest(Long upTo, @Min(0) long unitPriceMinor, @Min(0) long flatFeeMinor) {
    }

    public record PriceRequest(@NotBlank @Pattern(regexp = "[A-Z]{3}") String currency, @NotNull BillingInterval interval,
                               @NotNull PricingModel pricingModel, @Min(0) long basePriceMinor, List<TierRequest> tiers) {
        PlanService.PriceSpec toSpec() {
            return new PlanService.PriceSpec(Currency.getInstance(currency), interval, pricingModel, basePriceMinor,
                    tiers == null ? List.of() : tiers.stream()
                            .map(t -> new PlanService.TierSpec(t.upTo(), t.unitPriceMinor(), t.flatFeeMinor())).toList());
        }
    }

    public record CreatePlanRequest(@NotBlank @Pattern(regexp = "[a-z0-9-]+") String code, @NotBlank String name,
                                    @NotNull @Valid PriceRequest price) {
    }

    /** Active plans with their current version. Customers use this to pick what to switch to. */
    @GetMapping("/plans")
    public List<PlanDto> list() {
        return plans.listPlans().stream()
                .filter(Plan::isActive)
                .map(p -> PlanDto.from(p, plans.versionsOf(p.getId())))
                .toList();
    }

    @GetMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PlanDto> listAll() {
        return plans.listPlans().stream().map(p -> PlanDto.from(p, plans.versionsOf(p.getId()))).toList();
    }

    @GetMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlanDto get(@PathVariable UUID id) {
        return PlanDto.from(plans.getPlan(id), plans.versionsOf(id));
    }

    @PostMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto create(@Valid @RequestBody CreatePlanRequest request) {
        PlanVersion v = plans.createPlan(request.code(), request.name(), request.price().toSpec());
        return PlanDto.from(v.getPlan(), List.of(v));
    }

    @PostMapping("/admin/plans/{id}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanVersionDto publishVersion(@PathVariable UUID id, @Valid @RequestBody PriceRequest request) {
        return PlanVersionDto.from(plans.publishNewVersion(id, request.toSpec()));
    }

    @DeleteMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retire(@PathVariable UUID id) {
        plans.retire(id);
    }
}

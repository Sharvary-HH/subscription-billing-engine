package com.sharvary.billing.subscription;

import com.sharvary.billing.auth.AuthUser;
import com.sharvary.billing.auth.CurrentUser;
import com.sharvary.billing.common.IdempotencyService;
import com.sharvary.billing.common.dto.Dtos.EstimateDto;
import com.sharvary.billing.common.dto.Dtos.InvoiceDto;
import com.sharvary.billing.common.dto.Dtos.SubscriptionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final BillingService billing;
    private final IdempotencyService idempotency;

    public SubscriptionController(BillingService billing, IdempotencyService idempotency) {
        this.billing = billing;
        this.idempotency = idempotency;
    }

    public record SubscribeRequest(@NotNull UUID customerId, @NotNull UUID planVersionId, @Min(1) int quantity,
                                   @Min(0) int trialDays, List<UUID> meteredAddOnVersionIds) {
    }

    public record ChangeRequest(UUID planVersionId, @Min(1) int quantity) {
    }

    public record CancelRequest(boolean immediately) {
    }

    public record ChangeResponse(SubscriptionDto subscription, InvoiceDto invoice,
                                 com.sharvary.billing.common.Money credited, com.sharvary.billing.common.Money charged) {
    }

    // ---- admin -----------------------------------------------------------------------------

    @PostMapping("/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto subscribe(@RequestHeader("Idempotency-Key") @NotBlank String key,
                                     @Valid @RequestBody SubscribeRequest request) {
        return idempotency.execute("subscribe", key, request, SubscriptionDto.class,
                () -> SubscriptionDto.from(billing.subscribe(request.customerId(), request.planVersionId(),
                        request.quantity(), request.trialDays(), request.meteredAddOnVersionIds())));
    }

    @GetMapping("/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SubscriptionDto> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) SubscriptionStatus status) {
        return billing.search(q, status).stream().map(SubscriptionDto::from).toList();
    }

    @GetMapping("/admin/subscriptions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionDto get(@PathVariable UUID id) {
        return SubscriptionDto.from(billing.get(id));
    }

    @GetMapping("/admin/subscriptions/{id}/estimate")
    @PreAuthorize("hasRole('ADMIN')")
    public EstimateDto estimate(@PathVariable UUID id) {
        return EstimateDto.from(billing.estimateNextInvoice(id), billing.get(id).getCurrentPeriodEnd());
    }

    @PostMapping("/admin/subscriptions/{id}/change")
    @PreAuthorize("hasRole('ADMIN')")
    public ChangeResponse change(@PathVariable UUID id, @Valid @RequestBody ChangeRequest request) {
        return toResponse(billing.change(id, request.planVersionId(), request.quantity()));
    }

    @PostMapping("/admin/subscriptions/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionDto cancel(@PathVariable UUID id, @RequestBody CancelRequest request) {
        return SubscriptionDto.from(billing.cancel(id, request.immediately()));
    }

    @PostMapping("/admin/subscriptions/{id}/undo-cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionDto undoCancel(@PathVariable UUID id) {
        return SubscriptionDto.from(billing.undoScheduledCancel(id));
    }

    @PostMapping("/admin/subscriptions/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionDto pause(@PathVariable UUID id) {
        return SubscriptionDto.from(billing.pause(id));
    }

    @PostMapping("/admin/subscriptions/{id}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionDto resume(@PathVariable UUID id) {
        return SubscriptionDto.from(billing.resume(id));
    }

    // ---- customer --------------------------------------------------------------------------

    @GetMapping("/me/subscriptions")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<SubscriptionDto> mine() {
        return billing.listForCustomer(customerId()).stream().map(SubscriptionDto::from).toList();
    }

    @GetMapping("/me/subscriptions/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public SubscriptionDto getMine(@PathVariable UUID id) {
        return SubscriptionDto.from(billing.getForCustomer(id, customerId()));
    }

    @GetMapping("/me/subscriptions/{id}/estimate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public EstimateDto estimateMine(@PathVariable UUID id) {
        Subscription s = billing.getForCustomer(id, customerId());
        return EstimateDto.from(billing.estimateNextInvoice(s.getId()), s.getCurrentPeriodEnd());
    }

    @PostMapping("/me/subscriptions/{id}/change")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ChangeResponse changeMine(@PathVariable UUID id, @Valid @RequestBody ChangeRequest request) {
        Subscription s = billing.getForCustomer(id, customerId());
        return toResponse(billing.change(s.getId(), request.planVersionId(), request.quantity()));
    }

    @PostMapping("/me/subscriptions/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public SubscriptionDto cancelMine(@PathVariable UUID id, @RequestBody CancelRequest request) {
        Subscription s = billing.getForCustomer(id, customerId());
        return SubscriptionDto.from(billing.cancel(s.getId(), request.immediately()));
    }

    @PostMapping("/me/subscriptions/{id}/undo-cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public SubscriptionDto undoCancelMine(@PathVariable UUID id) {
        Subscription s = billing.getForCustomer(id, customerId());
        return SubscriptionDto.from(billing.undoScheduledCancel(s.getId()));
    }

    private static ChangeResponse toResponse(BillingService.ChangeResult r) {
        return new ChangeResponse(SubscriptionDto.from(r.subscription()), r.invoice().map(InvoiceDto::from).orElse(null),
                r.credited(), r.charged());
    }

    private static UUID customerId() {
        AuthUser user = CurrentUser.get();
        return user.customerId();
    }
}

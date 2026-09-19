package com.sharvary.billing.usage;

import com.sharvary.billing.auth.AuthUser;
import com.sharvary.billing.auth.CurrentUser;
import com.sharvary.billing.common.NotFoundException;
import com.sharvary.billing.common.dto.Dtos.UsageRecordDto;
import com.sharvary.billing.subscription.SubscriptionItem;
import com.sharvary.billing.subscription.SubscriptionItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageService usage;
    private final SubscriptionItemRepository items;
    private final Clock clock;

    public UsageController(UsageService usage, SubscriptionItemRepository items, Clock clock) {
        this.usage = usage;
        this.items = items;
        this.clock = clock;
    }

    public record UsageRequest(@NotNull UUID subscriptionItemId, @Min(1) long quantity, Instant recordedAt) {
    }

    /**
     * Idempotent on the header key. A replay returns the record created the first time. Customers
     * may only meter their own items; an item they do not own is a 404, not a 403.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsageRecordDto record(@RequestHeader("Idempotency-Key") @NotBlank String key,
                                 @Valid @RequestBody UsageRequest request) {
        requireOwnership(request.subscriptionItemId());
        Instant at = request.recordedAt() == null ? clock.instant() : request.recordedAt();
        return UsageRecordDto.from(usage.record(request.subscriptionItemId(), request.quantity(), at, key));
    }

    @GetMapping("/items/{itemId}")
    public List<UsageRecordDto> forItem(@PathVariable UUID itemId) {
        requireOwnership(itemId);
        return usage.recordsFor(itemId).stream().map(UsageRecordDto::from).toList();
    }

    private void requireOwnership(UUID itemId) {
        AuthUser user = CurrentUser.get();
        if (user.isAdmin()) {
            return;
        }
        SubscriptionItem item = items.findById(itemId).orElseThrow(() -> new NotFoundException("subscription item", itemId));
        if (!item.getSubscription().getCustomer().getId().equals(user.customerId())) {
            throw new NotFoundException("subscription item", itemId);
        }
    }
}

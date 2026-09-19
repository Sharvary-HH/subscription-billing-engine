package com.sharvary.billing.usage;

import com.sharvary.billing.common.BillingRuleException;
import com.sharvary.billing.common.Money;
import com.sharvary.billing.common.NotFoundException;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.plan.PriceTier;
import com.sharvary.billing.subscription.SubscriptionItem;
import com.sharvary.billing.subscription.SubscriptionItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsageService {

    private final UsageRecordRepository usage;
    private final SubscriptionItemRepository items;
    private final Clock clock;

    public UsageService(UsageRecordRepository usage, SubscriptionItemRepository items, Clock clock) {
        this.usage = usage;
        this.items = items;
        this.clock = clock;
    }

    /**
     * Appends a usage record. Same key twice returns the original record. Usage stamped before the
     * item's current period start belongs to a period that has already been invoiced, and an
     * invoice cannot change, so it is rejected rather than silently dropped or misattributed.
     */
    @Transactional
    public UsageRecord record(UUID itemId, long quantity, Instant recordedAt, String idempotencyKey) {
        Optional<UsageRecord> existing = usage.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        SubscriptionItem item = items.findById(itemId).orElseThrow(() -> new NotFoundException("subscription item", itemId));
        if (!item.getPlanVersion().getPricingModel().isMetered()) {
            throw new BillingRuleException("subscription item " + itemId + " is not metered");
        }
        Instant periodStart = item.getSubscription().getCurrentPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant();
        if (recordedAt.isBefore(periodStart)) {
            throw new BillingRuleException("usage at " + recordedAt + " falls in a closed billing period (current period starts "
                    + item.getSubscription().getCurrentPeriodStart() + ")");
        }
        if (recordedAt.isAfter(clock.instant())) {
            throw new BillingRuleException("usage cannot be recorded in the future");
        }
        return usage.save(new UsageRecord(UUID.randomUUID(), item, quantity, recordedAt, idempotencyKey, clock.instant()));
    }

    @Transactional(readOnly = true)
    public long quantityFor(UUID itemId, LocalDate from, LocalDate to) {
        return usage.totalQuantity(itemId, from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Transactional(readOnly = true)
    public List<UsageRecord> recordsFor(UUID itemId) {
        return usage.findByItemIdOrderByRecordedAtDesc(itemId);
    }

    /** What the item's usage over a window costs at its plan version's bands. */
    public static Money priceFor(PlanVersion version, long quantity) {
        List<PriceTier.Band> bands = version.getTiers().stream().map(PriceTier::toBand).toList();
        return MeteredPricing.price(version.getPricingModel(), bands, quantity, version.currency());
    }
}

package com.sharvary.billing.common.dto;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.customer.PaymentMethod;
import com.sharvary.billing.invoice.CreditNote;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.invoice.InvoiceKind;
import com.sharvary.billing.invoice.InvoiceLineItem;
import com.sharvary.billing.invoice.InvoiceStatus;
import com.sharvary.billing.invoice.LineType;
import com.sharvary.billing.invoice.allocation.InvoiceCalculator;
import com.sharvary.billing.payment.Notification;
import com.sharvary.billing.payment.PaymentAttempt;
import com.sharvary.billing.payment.dunning.DunningCase;
import com.sharvary.billing.payment.dunning.DunningState;
import com.sharvary.billing.plan.BillingInterval;
import com.sharvary.billing.plan.Plan;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.plan.PriceTier;
import com.sharvary.billing.plan.PricingModel;
import com.sharvary.billing.subscription.Subscription;
import com.sharvary.billing.subscription.SubscriptionItem;
import com.sharvary.billing.subscription.SubscriptionStatus;
import com.sharvary.billing.usage.UsageRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models for the API. Entities never leave the service layer. */
public final class Dtos {

    private Dtos() {
    }

    public record CustomerDto(UUID id, String name, String email, String currency, String taxRegion,
                              Money creditBalance, Instant createdAt) {
        public static CustomerDto from(Customer c) {
            return new CustomerDto(c.getId(), c.getName(), c.getEmail(), c.currency().getCurrencyCode(), c.getTaxRegion(),
                    c.creditBalance(), c.getCreatedAt());
        }
    }

    public record PaymentMethodDto(UUID id, String brand, String last4, boolean isDefault, String token, Instant createdAt) {
        public static PaymentMethodDto from(PaymentMethod m) {
            return new PaymentMethodDto(m.getId(), m.getBrand(), m.getLast4(), m.isDefaultMethod(), m.getProviderToken(),
                    m.getCreatedAt());
        }
    }

    public record TierDto(int index, Long upTo, long unitPriceMinor, long flatFeeMinor) {
        public static TierDto from(PriceTier t) {
            return new TierDto(t.getTierIndex(), t.getUpTo(), t.getUnitPriceMinor(), t.getFlatFeeMinor());
        }
    }

    public record PlanVersionDto(UUID id, UUID planId, String planCode, String planName, int version, String currency,
                                 BillingInterval interval, PricingModel pricingModel, Money basePrice,
                                 List<TierDto> tiers, Instant createdAt) {
        public static PlanVersionDto from(PlanVersion v) {
            return new PlanVersionDto(v.getId(), v.getPlan().getId(), v.getPlan().getCode(), v.getPlan().getName(),
                    v.getVersion(), v.currency().getCurrencyCode(), v.getBillingInterval(), v.getPricingModel(),
                    v.basePrice(), v.getTiers().stream().map(TierDto::from).toList(), v.getCreatedAt());
        }
    }

    public record PlanDto(UUID id, String code, String name, boolean active, PlanVersionDto currentVersion,
                          List<PlanVersionDto> versions) {
        public static PlanDto from(Plan p, List<PlanVersion> versions) {
            List<PlanVersionDto> vs = versions.stream().map(PlanVersionDto::from).toList();
            return new PlanDto(p.getId(), p.getCode(), p.getName(), p.isActive(), vs.isEmpty() ? null : vs.get(0), vs);
        }
    }

    public record SubscriptionItemDto(UUID id, PlanVersionDto planVersion, int quantity) {
        public static SubscriptionItemDto from(SubscriptionItem i) {
            return new SubscriptionItemDto(i.getId(), PlanVersionDto.from(i.getPlanVersion()), i.getQuantity());
        }
    }

    public record SubscriptionDto(UUID id, UUID customerId, String customerName, String customerEmail,
                                  PlanVersionDto planVersion, int quantity, SubscriptionStatus status, int anchorDay,
                                  LocalDate currentPeriodStart, LocalDate currentPeriodEnd, LocalDate trialEnd,
                                  LocalDate cancelAt, Instant canceledAt, Money periodPrice,
                                  List<SubscriptionItemDto> items, Instant createdAt) {
        public static SubscriptionDto from(Subscription s) {
            return new SubscriptionDto(s.getId(), s.getCustomer().getId(), s.getCustomer().getName(),
                    s.getCustomer().getEmail(), PlanVersionDto.from(s.getPlanVersion()), s.getQuantity(), s.getStatus(),
                    s.getAnchorDay(), s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getTrialEnd(), s.getCancelAt(),
                    s.getCanceledAt(), s.periodPrice(), s.getItems().stream().map(SubscriptionItemDto::from).toList(),
                    s.getCreatedAt());
        }
    }

    public record LineDto(int index, LineType type, String description, long quantity, Money unitPrice, Money amount,
                          Money tax, LocalDate periodStart, LocalDate periodEnd, boolean proration) {
        public static LineDto from(InvoiceLineItem l) {
            return new LineDto(l.getLineIndex(), l.getType(), l.getDescription(), l.getQuantity(), l.unitPrice(),
                    l.amount(), l.tax(), l.getPeriodStart(), l.getPeriodEnd(), l.isProration());
        }
    }

    public record InvoiceDto(UUID id, String invoiceNumber, UUID customerId, String customerName, UUID subscriptionId,
                             InvoiceStatus status, InvoiceKind kind, String currency, LocalDate periodStart,
                             LocalDate periodEnd, Money subtotal, Money tax, Money total, Money amountPaid,
                             Money amountDue, Instant issuedAt, Instant dueAt, Instant paidAt, List<LineDto> lines,
                             Instant createdAt) {
        public static InvoiceDto from(Invoice i) {
            return new InvoiceDto(i.getId(), i.getInvoiceNumber(), i.getCustomer().getId(), i.getCustomer().getName(),
                    i.getSubscription() == null ? null : i.getSubscription().getId(), i.getStatus(), i.getKind(),
                    i.currency().getCurrencyCode(), i.getPeriodStart(), i.getPeriodEnd(), i.subtotal(), i.tax(),
                    i.total(), i.amountPaid(), i.amountDue(), i.getIssuedAt(), i.getDueAt(), i.getPaidAt(),
                    i.getLines().stream().map(LineDto::from).toList(), i.getCreatedAt());
        }
    }

    public record EstimateLineDto(LineType type, String description, long quantity, Money amount, Money tax,
                                  LocalDate periodStart, LocalDate periodEnd, boolean proration) {
    }

    public record EstimateDto(List<EstimateLineDto> lines, Money subtotal, Money tax, Money creditApplied, Money total,
                              LocalDate nextBillingDate) {
        public static EstimateDto from(InvoiceCalculator.Result r, LocalDate nextBillingDate) {
            return new EstimateDto(r.lines().stream().map(l -> new EstimateLineDto(l.line().type(),
                            l.line().description(), l.line().quantity(), l.line().amount(), l.tax(),
                            l.line().periodStart(), l.line().periodEnd(), l.line().proration())).toList(),
                    r.subtotal(), r.tax(), r.creditApplied(), r.total(), nextBillingDate);
        }
    }

    public record PaymentAttemptDto(UUID id, int attemptNumber, PaymentAttempt.Status status, Money amount,
                                    String providerRef, String failureReason, String triggeredBy, Instant createdAt) {
        public static PaymentAttemptDto from(PaymentAttempt a) {
            return new PaymentAttemptDto(a.getId(), a.getAttemptNumber(), a.getStatus(), a.amount(), a.getProviderRef(),
                    a.getFailureReason(), a.getTriggeredBy(), a.getCreatedAt());
        }
    }

    public record DunningCaseDto(UUID id, DunningState state, int retriesDone, int maxRetries, Instant nextRetryAt,
                                 Instant startedAt, Instant resolvedAt, InvoiceDto invoice, UUID subscriptionId,
                                 List<PaymentAttemptDto> attempts) {
        public static DunningCaseDto from(DunningCase c, int maxRetries, List<PaymentAttempt> attempts) {
            return new DunningCaseDto(c.getId(), c.getState(), c.getRetriesDone(), maxRetries, c.getNextRetryAt(),
                    c.getStartedAt(), c.getResolvedAt(), InvoiceDto.from(c.getInvoice()), c.getSubscription().getId(),
                    attempts.stream().map(PaymentAttemptDto::from).toList());
        }
    }

    public record CreditNoteLineDto(int index, String description, Money amount) {
    }

    public record CreditNoteDto(UUID id, String creditNoteNumber, UUID invoiceId, String reason, Money total,
                                List<CreditNoteLineDto> lines, Instant createdAt) {
        public static CreditNoteDto from(CreditNote n) {
            return new CreditNoteDto(n.getId(), n.getCreditNoteNumber(), n.getInvoice().getId(), n.getReason(), n.total(),
                    n.getLines().stream().map(l -> new CreditNoteLineDto(l.getLineIndex(), l.getDescription(), l.amount())).toList(),
                    n.getCreatedAt());
        }
    }

    public record UsageRecordDto(UUID id, UUID subscriptionItemId, long quantity, Instant recordedAt, String idempotencyKey) {
        public static UsageRecordDto from(UsageRecord u) {
            return new UsageRecordDto(u.getId(), u.getItem().getId(), u.getQuantity(), u.getRecordedAt(), u.getIdempotencyKey());
        }
    }

    public record NotificationDto(UUID id, UUID customerId, String kind, String subject, String body, Instant createdAt) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getCustomerId(), n.getKind(), n.getSubject(), n.getBody(), n.getCreatedAt());
        }
    }
}

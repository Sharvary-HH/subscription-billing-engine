package com.sharvary.billing.invoice;

import com.sharvary.billing.common.IllegalStateTransitionException;
import com.sharvary.billing.common.Money;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.subscription.Subscription;
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
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceKind kind;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "amount_paid_minor", nullable = false)
    private long amountPaidMinor;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("lineIndex asc")
    private List<InvoiceLineItem> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Invoice() {
    }

    public Invoice(UUID id, String invoiceNumber, Customer customer, Subscription subscription, InvoiceKind kind,
                   Currency currency, LocalDate periodStart, LocalDate periodEnd, Instant createdAt) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.customer = customer;
        this.subscription = subscription;
        this.status = InvoiceStatus.DRAFT;
        this.kind = kind;
        this.currency = currency.getCurrencyCode();
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.createdAt = createdAt;
    }

    /** Only a draft can take lines. Issued invoices are immutable; corrections are credit notes. */
    public InvoiceLineItem addLine(LineType type, String description, long quantity, Money unitPrice, Money amount,
                                   Money tax, LocalDate lineStart, LocalDate lineEnd, boolean proration) {
        requireDraft();
        requireCurrency(amount);
        InvoiceLineItem line = new InvoiceLineItem(UUID.randomUUID(), this, lines.size(), type, description, quantity,
                unitPrice.minor(), amount.minor(), tax.minor(), lineStart, lineEnd, proration);
        lines.add(line);
        return line;
    }

    public void setTotals(Money subtotal, Money tax, Money total) {
        requireDraft();
        requireCurrency(subtotal);
        this.subtotalMinor = subtotal.minor();
        this.taxMinor = tax.minor();
        this.totalMinor = total.minor();
    }

    public void issue(Instant at, Instant due) {
        transitionTo(InvoiceStatus.OPEN);
        this.issuedAt = at;
        this.dueAt = due;
    }

    public void markPaid(Money amount, Instant at) {
        requireCurrency(amount);
        transitionTo(InvoiceStatus.PAID);
        this.amountPaidMinor = amount.minor();
        this.paidAt = at;
    }

    public void markUncollectible() {
        transitionTo(InvoiceStatus.UNCOLLECTIBLE);
    }

    public void markVoid() {
        transitionTo(InvoiceStatus.VOID);
    }

    public void markRefunded() {
        transitionTo(InvoiceStatus.REFUNDED);
    }

    public void transitionTo(InvoiceStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException("invoice " + invoiceNumber, status, next);
        }
        this.status = next;
    }

    public Currency currency() {
        return Currency.getInstance(currency);
    }

    public Money subtotal() {
        return Money.of(subtotalMinor, currency);
    }

    public Money tax() {
        return Money.of(taxMinor, currency);
    }

    public Money total() {
        return Money.of(totalMinor, currency);
    }

    public Money amountPaid() {
        return Money.of(amountPaidMinor, currency);
    }

    public Money amountDue() {
        return total().minus(amountPaid());
    }

    private void requireDraft() {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("invoice " + invoiceNumber + " is " + status + " and cannot be edited");
        }
    }

    private void requireCurrency(Money money) {
        if (!money.currency().equals(currency())) {
            throw new IllegalArgumentException("invoice is in " + currency + ", got " + money.currencyCode());
        }
    }

    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public InvoiceKind getKind() {
        return kind;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public List<InvoiceLineItem> getLines() {
        return List.copyOf(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

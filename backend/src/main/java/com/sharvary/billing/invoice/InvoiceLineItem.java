package com.sharvary.billing.invoice;

import com.sharvary.billing.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items")
public class InvoiceLineItem {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "line_index", nullable = false)
    private int lineIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false)
    private LineType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private boolean proration;

    protected InvoiceLineItem() {
    }

    InvoiceLineItem(UUID id, Invoice invoice, int lineIndex, LineType type, String description, long quantity,
                    long unitPriceMinor, long amountMinor, long taxMinor, LocalDate periodStart, LocalDate periodEnd,
                    boolean proration) {
        this.id = id;
        this.invoice = invoice;
        this.lineIndex = lineIndex;
        this.type = type;
        this.description = description;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.amountMinor = amountMinor;
        this.taxMinor = taxMinor;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.proration = proration;
    }

    public Money unitPrice() {
        return Money.of(unitPriceMinor, invoice.currency());
    }

    public Money amount() {
        return Money.of(amountMinor, invoice.currency());
    }

    public Money tax() {
        return Money.of(taxMinor, invoice.currency());
    }

    public UUID getId() {
        return id;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public LineType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public long getQuantity() {
        return quantity;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public boolean isProration() {
        return proration;
    }
}

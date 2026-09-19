package com.sharvary.billing.invoice;

import com.sharvary.billing.common.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** The only way to correct an issued invoice. Append-only, like the invoice it points at. */
@Entity
@Table(name = "credit_notes")
public class CreditNote {

    @Id
    private UUID id;

    @Column(name = "credit_note_number", nullable = false)
    private String creditNoteNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("lineIndex asc")
    private List<CreditNoteLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditNote() {
    }

    public CreditNote(UUID id, String creditNoteNumber, Invoice invoice, String reason, Currency currency, Instant createdAt) {
        this.id = id;
        this.creditNoteNumber = creditNoteNumber;
        this.invoice = invoice;
        this.reason = reason;
        this.currency = currency.getCurrencyCode();
        this.createdAt = createdAt;
    }

    public void addLine(String description, Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("credit note lines must be positive");
        }
        lines.add(new CreditNoteLine(UUID.randomUUID(), this, lines.size(), description, amount.minor()));
        totalMinor = total().plus(amount).minor();
    }

    public Money total() {
        return Money.of(totalMinor, currency);
    }

    public UUID getId() {
        return id;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public String getReason() {
        return reason;
    }

    public List<CreditNoteLine> getLines() {
        return List.copyOf(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

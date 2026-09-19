package com.sharvary.billing.invoice;

import com.sharvary.billing.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "credit_note_lines")
public class CreditNoteLine {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "credit_note_id")
    private CreditNote creditNote;

    @Column(name = "line_index", nullable = false)
    private int lineIndex;

    @Column(nullable = false)
    private String description;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected CreditNoteLine() {
    }

    CreditNoteLine(UUID id, CreditNote creditNote, int lineIndex, String description, long amountMinor) {
        this.id = id;
        this.creditNote = creditNote;
        this.lineIndex = lineIndex;
        this.description = description;
        this.amountMinor = amountMinor;
    }

    public Money amount() {
        return Money.of(amountMinor, creditNote.total().currency());
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public String getDescription() {
        return description;
    }
}

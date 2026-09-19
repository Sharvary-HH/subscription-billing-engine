package com.sharvary.billing.invoice;

import com.sharvary.billing.common.BillingRuleException;
import com.sharvary.billing.common.Money;
import com.sharvary.billing.common.NotFoundException;
import com.sharvary.billing.payment.PaymentProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Credit notes against paid invoices. The invoice itself is never touched beyond its status:
 * the credit note is a new document, the refund goes back through the provider, and when the
 * credits reach the invoice total the invoice becomes REFUNDED.
 *
 * <p>Open invoices are not credited; they are voided. There is nothing to refund yet.
 */
@Service
public class CreditNoteService {

    public record LineSpec(String description, long amountMinor) {
    }

    private final CreditNoteRepository creditNotes;
    private final InvoiceRepository invoices;
    private final PaymentProvider provider;
    private final Clock clock;

    public CreditNoteService(CreditNoteRepository creditNotes, InvoiceRepository invoices, PaymentProvider provider,
                             Clock clock) {
        this.creditNotes = creditNotes;
        this.invoices = invoices;
        this.provider = provider;
        this.clock = clock;
    }

    @Transactional
    public CreditNote issue(UUID invoiceId, String reason, List<LineSpec> lines) {
        Invoice invoice = invoices.findByIdForUpdate(invoiceId).orElseThrow(() -> new NotFoundException("invoice", invoiceId));
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw new BillingRuleException("credit notes can only be issued against paid invoices; "
                    + invoice.getInvoiceNumber() + " is " + invoice.getStatus());
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("a credit note needs at least one line");
        }

        Money alreadyCredited = creditedSoFar(invoiceId, invoice);
        CreditNote note = new CreditNote(UUID.randomUUID(), "CN-" + String.format("%06d", creditNotes.nextNumber()),
                invoice, reason, invoice.currency(), clock.instant());
        for (LineSpec line : lines) {
            note.addLine(line.description(), Money.of(line.amountMinor(), invoice.currency()));
        }

        Money remaining = invoice.total().minus(alreadyCredited);
        if (note.total().compareTo(remaining) > 0) {
            throw new BillingRuleException("credit " + note.total() + " exceeds the " + remaining
                    + " still creditable on " + invoice.getInvoiceNumber());
        }

        provider.refund(new PaymentProvider.RefundRequest(note.getCreditNoteNumber(), note.total(), invoice.getInvoiceNumber()));
        if (note.total().equals(remaining)) {
            invoice.markRefunded();
        }
        return creditNotes.save(note);
    }

    @Transactional(readOnly = true)
    public List<CreditNote> forInvoice(UUID invoiceId) {
        return creditNotes.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }

    private Money creditedSoFar(UUID invoiceId, Invoice invoice) {
        Money sum = Money.zero(invoice.currency());
        for (CreditNote existing : creditNotes.findByInvoiceIdOrderByCreatedAtAsc(invoiceId)) {
            sum = sum.plus(existing.total());
        }
        return sum;
    }
}

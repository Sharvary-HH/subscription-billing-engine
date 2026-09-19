package com.sharvary.billing.invoice;

import com.sharvary.billing.common.BillingRuleException;
import com.sharvary.billing.common.Money;
import com.sharvary.billing.common.NotFoundException;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.customer.CustomerRepository;
import com.sharvary.billing.invoice.allocation.InvoiceCalculator;
import com.sharvary.billing.subscription.Subscription;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Builds, issues and closes invoices. Does not decide what goes on them; the subscription side
 * decides the lines, this side makes the numbers add up and enforces immutability after issue.
 */
@Service
public class InvoiceService {

    static final Duration PAYMENT_TERMS = Duration.ofDays(7);

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, CustomerRepository customers, Clock clock) {
        this.invoices = invoices;
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Creates and issues an invoice in one go. The customer's credit balance is drawn as part of
     * the calculation, under a row lock, so two invoices generated at once cannot both spend it.
     */
    @Transactional
    public Invoice issue(Customer customerRef, Subscription subscription, InvoiceKind kind, LocalDate periodStart,
                         LocalDate periodEnd, List<InvoiceCalculator.ProposedLine> lines) {
        Customer customer = customers.findByIdForUpdate(customerRef.getId())
                .orElseThrow(() -> new NotFoundException("customer", customerRef.getId()));
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

        InvoiceCalculator.Result computed = InvoiceCalculator.compute(
                lines, TaxRates.forRegion(customer.getTaxRegion()), customer.creditBalance(), today);

        Invoice invoice = new Invoice(UUID.randomUUID(), nextNumber(), customer, subscription, kind,
                customer.currency(), periodStart, periodEnd, clock.instant());
        for (InvoiceCalculator.ComputedLine line : computed.lines()) {
            var l = line.line();
            invoice.addLine(l.type(), l.description(), l.quantity(), l.unitPrice(), l.amount(), line.tax(),
                    l.periodStart(), l.periodEnd(), l.proration());
        }
        invoice.setTotals(computed.subtotal(), computed.tax(), computed.total());
        if (computed.creditApplied().isPositive()) {
            customer.drawCredit(computed.creditApplied());
        }

        Instant now = clock.instant();
        invoice.issue(now, now.plus(PAYMENT_TERMS));
        if (invoice.total().isZero()) {
            // Nothing to collect. Fully covered by credit, or a free plan.
            invoice.markPaid(Money.zero(invoice.currency()), now);
        }
        return invoices.saveAndFlush(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("invoice", id));
    }

    /** Customer-scoped lookup. A miss and a foreign invoice both come back as 404. */
    @Transactional(readOnly = true)
    public Invoice getForCustomer(UUID id, UUID customerId) {
        return invoices.findByIdAndCustomerId(id, customerId).orElseThrow(() -> new NotFoundException("invoice", id));
    }

    @Transactional(readOnly = true)
    public List<Invoice> listForCustomer(UUID customerId) {
        return invoices.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> listByStatus(InvoiceStatus status) {
        return invoices.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public Invoice voidInvoice(UUID id) {
        Invoice invoice = invoices.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("invoice", id));
        invoice.markVoid();
        return invoice;
    }

    /** Returns the estimated lines and totals for the next invoice without persisting anything. */
    public InvoiceCalculator.Result estimate(Customer customer, List<InvoiceCalculator.ProposedLine> lines) {
        if (lines.isEmpty()) {
            return new InvoiceCalculator.Result(List.of(), Money.zero(customer.currency()), Money.zero(customer.currency()),
                    Money.zero(customer.currency()), Money.zero(customer.currency()));
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return InvoiceCalculator.compute(lines, TaxRates.forRegion(customer.getTaxRegion()), customer.creditBalance(), today);
    }

    private String nextNumber() {
        return "INV-" + String.format("%06d", invoices.nextInvoiceNumber());
    }

    static void requireIssued(Invoice invoice) {
        if (!invoice.getStatus().isIssued()) {
            throw new BillingRuleException("invoice " + invoice.getInvoiceNumber() + " has not been issued");
        }
    }
}

package com.sharvary.billing.invoice;

import com.sharvary.billing.auth.AuthUser;
import com.sharvary.billing.auth.CurrentUser;
import com.sharvary.billing.common.dto.Dtos.CreditNoteDto;
import com.sharvary.billing.common.dto.Dtos.InvoiceDto;
import com.sharvary.billing.common.dto.Dtos.PaymentAttemptDto;
import com.sharvary.billing.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceService invoices;
    private final PaymentService payments;
    private final CreditNoteService creditNotes;

    public InvoiceController(InvoiceService invoices, PaymentService payments, CreditNoteService creditNotes) {
        this.invoices = invoices;
        this.payments = payments;
        this.creditNotes = creditNotes;
    }

    public record CreditNoteLineRequest(@NotBlank String description, @Min(1) long amountMinor) {
    }

    public record CreditNoteRequest(@NotBlank String reason, @NotEmpty List<@Valid CreditNoteLineRequest> lines) {
    }

    public record PayResponse(InvoiceDto invoice, PaymentAttemptDto attempt) {
    }

    // ---- admin -----------------------------------------------------------------------------

    @GetMapping("/admin/invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public List<InvoiceDto> list(@RequestParam(required = false) InvoiceStatus status,
                                 @RequestParam(required = false) UUID customerId) {
        List<Invoice> result = customerId != null ? invoices.listForCustomer(customerId)
                : status != null ? invoices.listByStatus(status) : invoices.listByStatus(InvoiceStatus.OPEN);
        return result.stream().map(InvoiceDto::from).toList();
    }

    @GetMapping("/admin/invoices/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceDto get(@PathVariable UUID id) {
        return InvoiceDto.from(invoices.get(id));
    }

    @GetMapping("/admin/invoices/{id}/attempts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentAttemptDto> attempts(@PathVariable UUID id) {
        invoices.get(id);
        return payments.attemptsFor(id).stream().map(PaymentAttemptDto::from).toList();
    }

    @PostMapping("/admin/invoices/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public PayResponse pay(@PathVariable UUID id) {
        var attempt = payments.collect(id, PaymentService.TRIGGER_MANUAL);
        return new PayResponse(InvoiceDto.from(invoices.get(id)), attempt.map(PaymentAttemptDto::from).orElse(null));
    }

    @PostMapping("/admin/invoices/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceDto voidInvoice(@PathVariable UUID id) {
        return InvoiceDto.from(invoices.voidInvoice(id));
    }

    @PostMapping("/admin/invoices/{id}/credit-notes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreditNoteDto creditNote(@PathVariable UUID id, @Valid @RequestBody CreditNoteRequest request) {
        return CreditNoteDto.from(creditNotes.issue(id, request.reason(), request.lines().stream()
                .map(l -> new CreditNoteService.LineSpec(l.description(), l.amountMinor())).toList()));
    }

    @GetMapping("/admin/invoices/{id}/credit-notes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CreditNoteDto> creditNotes(@PathVariable UUID id) {
        invoices.get(id);
        return creditNotes.forInvoice(id).stream().map(CreditNoteDto::from).toList();
    }

    // ---- customer --------------------------------------------------------------------------

    @GetMapping("/me/invoices")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<InvoiceDto> mine() {
        return invoices.listForCustomer(customerId()).stream().map(InvoiceDto::from).toList();
    }

    @GetMapping("/me/invoices/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public InvoiceDto getMine(@PathVariable UUID id) {
        return InvoiceDto.from(invoices.getForCustomer(id, customerId()));
    }

    @GetMapping("/me/invoices/{id}/attempts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<PaymentAttemptDto> myAttempts(@PathVariable UUID id) {
        invoices.getForCustomer(id, customerId());
        return payments.attemptsFor(id).stream().map(PaymentAttemptDto::from).toList();
    }

    @PostMapping("/me/invoices/{id}/pay")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PayResponse payMine(@PathVariable UUID id) {
        Invoice invoice = invoices.getForCustomer(id, customerId());
        var attempt = payments.collect(invoice.getId(), PaymentService.TRIGGER_MANUAL);
        return new PayResponse(InvoiceDto.from(invoices.get(id)), attempt.map(PaymentAttemptDto::from).orElse(null));
    }

    @GetMapping("/me/invoices/{id}/credit-notes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<CreditNoteDto> myCreditNotes(@PathVariable UUID id) {
        invoices.getForCustomer(id, customerId());
        return creditNotes.forInvoice(id).stream().map(CreditNoteDto::from).toList();
    }

    private static UUID customerId() {
        AuthUser user = CurrentUser.get();
        return user.customerId();
    }
}

package com.sharvary.billing.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    List<CreditNote> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    @Query(value = "select nextval('credit_note_number_seq')", nativeQuery = true)
    long nextNumber();
}

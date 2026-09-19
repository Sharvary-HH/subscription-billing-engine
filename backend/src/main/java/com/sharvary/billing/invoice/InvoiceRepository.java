package com.sharvary.billing.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<Invoice> findByIdAndCustomerId(UUID id, UUID customerId);

    List<Invoice> findBySubscriptionIdOrderByCreatedAtAsc(UUID subscriptionId);

    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    long countBySubscriptionIdAndPeriodStartAndKind(UUID subscriptionId, LocalDate periodStart, InvoiceKind kind);

    /** Row lock so a scheduled retry and a manual "pay now" cannot both charge the same invoice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceNumber();

    @Query("select i from Invoice i join fetch i.customer where i.status in :statuses order by i.createdAt desc")
    List<Invoice> findByStatusIn(@Param("statuses") List<InvoiceStatus> statuses);
}

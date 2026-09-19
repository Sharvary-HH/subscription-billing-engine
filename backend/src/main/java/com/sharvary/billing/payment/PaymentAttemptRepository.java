package com.sharvary.billing.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByInvoiceIdOrderByAttemptNumberAsc(UUID invoiceId);

    Optional<PaymentAttempt> findFirstByInvoiceIdOrderByAttemptNumberDesc(UUID invoiceId);

    List<PaymentAttempt> findByInvoiceCustomerIdOrderByCreatedAtDesc(UUID customerId);
}

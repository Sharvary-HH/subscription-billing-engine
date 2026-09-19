package com.sharvary.billing.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);

    Optional<PaymentMethod> findByCustomerIdAndDefaultMethodTrue(UUID customerId);
}

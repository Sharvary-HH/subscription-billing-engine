package com.sharvary.billing.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Notification> findTop50ByOrderByCreatedAtDesc();
}

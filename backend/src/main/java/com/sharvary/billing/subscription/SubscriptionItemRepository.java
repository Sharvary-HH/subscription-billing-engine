package com.sharvary.billing.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubscriptionItemRepository extends JpaRepository<SubscriptionItem, UUID> {
}

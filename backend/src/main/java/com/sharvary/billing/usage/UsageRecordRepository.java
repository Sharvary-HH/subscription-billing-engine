package com.sharvary.billing.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    Optional<UsageRecord> findByIdempotencyKey(String key);

    @Query("select coalesce(sum(u.quantity), 0) from UsageRecord u " +
            "where u.item.id = :itemId and u.recordedAt >= :from and u.recordedAt < :to")
    long totalQuantity(@Param("itemId") UUID itemId, @Param("from") Instant from, @Param("to") Instant to);

    List<UsageRecord> findByItemIdOrderByRecordedAtDesc(UUID itemId);
}

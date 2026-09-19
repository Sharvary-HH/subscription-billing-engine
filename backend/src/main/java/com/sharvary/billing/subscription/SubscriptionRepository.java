package com.sharvary.billing.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<Subscription> findByIdAndCustomerId(UUID id, UUID customerId);

    @Query("select s from Subscription s join fetch s.customer c join fetch s.planVersion v join fetch v.plan " +
            "where s.status in :statuses " +
            "and (lower(c.name) like :pattern or lower(c.email) like :pattern) " +
            "order by s.createdAt desc")
    List<Subscription> search(@Param("pattern") String pattern, @Param("statuses") Collection<SubscriptionStatus> statuses);

    /**
     * Claims one subscription that is due for billing. {@code FOR UPDATE SKIP LOCKED} means a
     * second job instance running at the same moment skips rows this one holds instead of
     * blocking on them or, worse, billing them again.
     */
    @Query(value = """
            select * from subscriptions
            where current_period_end <= :today
              and status in ('TRIALING', 'ACTIVE', 'PAST_DUE')
              and id not in (:skip)
            order by current_period_end
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<Subscription> claimNextDue(@Param("today") LocalDate today, @Param("skip") Collection<UUID> skip);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") UUID id);

    @Query("select s from Subscription s where s.customer.id = :customerId and s.status in :statuses")
    List<Subscription> findByCustomerIdAndStatusIn(@Param("customerId") UUID customerId,
                                                   @Param("statuses") Collection<SubscriptionStatus> statuses);

    long countByStatus(SubscriptionStatus status);
}

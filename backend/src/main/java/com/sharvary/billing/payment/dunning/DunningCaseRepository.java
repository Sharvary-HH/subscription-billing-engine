package com.sharvary.billing.payment.dunning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DunningCaseRepository extends JpaRepository<DunningCase, UUID> {

    Optional<DunningCase> findByInvoiceId(UUID invoiceId);

    List<DunningCase> findByStateOrderByNextRetryAtAsc(DunningState state);

    @Query("select d from DunningCase d where d.state = :state and d.subscription.customer.id = :customerId")
    List<DunningCase> findByStateAndCustomer(@Param("state") DunningState state, @Param("customerId") UUID customerId);

    List<DunningCase> findBySubscriptionIdAndState(UUID subscriptionId, DunningState state);

    /** Same claim pattern as the billing job: one due case, locked, others skipped. */
    @Query(value = """
            select * from dunning_cases
            where state = 'RETRYING'
              and next_retry_at <= :now
              and id not in (:skip)
            order by next_retry_at
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<DunningCase> claimNextDue(@Param("now") Instant now, @Param("skip") Collection<UUID> skip);
}

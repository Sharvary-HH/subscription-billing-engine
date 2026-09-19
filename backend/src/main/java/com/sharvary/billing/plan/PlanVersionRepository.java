package com.sharvary.billing.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {

    List<PlanVersion> findByPlanIdOrderByVersionDesc(UUID planId);

    Optional<PlanVersion> findFirstByPlanIdOrderByVersionDesc(UUID planId);
}

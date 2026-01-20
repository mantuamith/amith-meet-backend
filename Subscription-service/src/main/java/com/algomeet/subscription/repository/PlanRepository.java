package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByIsActiveTrueOrderByIdAsc();
}

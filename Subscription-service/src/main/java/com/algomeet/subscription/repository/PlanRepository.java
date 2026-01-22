package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository
        extends JpaRepository<Plan, UUID>, PlanRepositoryCustom {

    Optional<Plan> findByCode(String code);

    List<Plan> findByIsActiveTrueOrderByIdAsc();
}

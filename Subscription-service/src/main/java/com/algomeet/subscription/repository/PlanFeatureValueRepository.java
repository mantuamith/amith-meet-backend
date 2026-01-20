package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.PlanFeatureValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PlanFeatureValueRepository
        extends JpaRepository<PlanFeatureValue, UUID> {

    @Query("""
        SELECT pfv
        FROM PlanFeatureValue pfv
        JOIN FETCH pfv.plan
        JOIN FETCH pfv.featureProperty fp
        JOIN FETCH fp.feature
    """)
    List<PlanFeatureValue> findAllForComparison();
}

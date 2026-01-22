package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.PlanFeatureValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanFeatureValueRepository
        extends JpaRepository<PlanFeatureValue, UUID> {
    Optional<PlanFeatureValue> findByPlanIdAndFeaturePropertyId(
            UUID planId,
            UUID featurePropertyId
    );

    @Query("""
    select pfv
    from PlanFeatureValue pfv
    join fetch pfv.featureProperty fp
    join fetch fp.feature f
    join fetch pfv.plan p
    where p.code = :planCode
""")
    List<PlanFeatureValue> findAllByPlanCode(String planCode);

    @Query("""
        select pfv
        from PlanFeatureValue pfv
        join fetch pfv.featureProperty fp
        join fetch fp.feature f
        where pfv.plan.id = :planId
        order by f.displayOrder, fp.label
    """)
    List<PlanFeatureValue> findAllByPlanId(UUID planId);

    @Query("""
        SELECT pfv
        FROM PlanFeatureValue pfv
        JOIN FETCH pfv.plan
        JOIN FETCH pfv.featureProperty fp
        JOIN FETCH fp.feature
    """)
    List<PlanFeatureValue> findAllForComparison();
}

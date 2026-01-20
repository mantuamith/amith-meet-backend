package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.FeatureProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FeaturePropertyRepository
        extends JpaRepository<FeatureProperty, UUID> {

    @Query("""
        SELECT fp
        FROM FeatureProperty fp
        JOIN FETCH fp.feature f
        ORDER BY f.displayOrder ASC, fp.label ASC
    """)
    List<FeatureProperty> findAllWithFeature();
}

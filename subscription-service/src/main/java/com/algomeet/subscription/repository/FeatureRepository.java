package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    boolean existsByCode(String code);
}

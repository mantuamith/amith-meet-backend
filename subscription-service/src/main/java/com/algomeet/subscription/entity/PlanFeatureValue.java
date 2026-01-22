package com.algomeet.subscription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "plan_feature_values")
@Getter
@Setter
public class PlanFeatureValue {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_property_id", nullable = false)
    private FeatureProperty featureProperty;

    @Column(nullable = false)
    private String value;
}

package com.algomeet.subscription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "feature_properties")
@Getter
@Setter
public class FeatureProperty {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(name = "prop_key", nullable = false)
    private String propKey;

    @Column(nullable = false)
    private String label;

    @Column(name = "value_type", nullable = false)
    private String valueType;
}

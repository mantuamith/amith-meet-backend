package com.algomeet.subscription.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "features")
@Getter
@Setter
public class Feature {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    private String name;

    @Column(name = "ui_group")
    private String uiGroup;

    @Column(name = "display_order")
    private Integer displayOrder;
}

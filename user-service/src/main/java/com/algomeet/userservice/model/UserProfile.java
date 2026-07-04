package com.algomeet.userservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_profile")
public class UserProfile {
	/**
	 * Used to link to users table
	 */
    @Id   
    private UUID id;

    /**
     * 0:any, 1:mobile, 2:web, 3:desktop
     * Matches Step-1R migration: login_type_policy SMALLINT NOT NULL DEFAULT 0
     */
    @Column(name = "login_type_policy", nullable = false)
    private Short loginTypePolicy = 0;

    private String country;
    private String region;
    private String city;

    @Column(precision = 9, scale = 6)       // numeric(9,6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "registration_device_id", length = 128)
    private String registrationDeviceId;
    
    @Column(name = "registration_device_type", length = 32)
    private String registrationDeviceType;

    private Instant registrationDate;
    
    @Column(length = 8)
    private String passcode;
    
    private Boolean securityQuestionsEnabled;
    
    @Column(name = "message_retention_days", nullable = false, columnDefinition = "INT DEFAULT -1")
    private Integer messageRetentionDays = -1;
    
    @PrePersist
    protected void onCreate() {
        this.registrationDate = Instant.now();
    } 
}

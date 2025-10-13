package com.algomeet.userservice.model;

import jakarta.persistence.*;
import lombok.*;


import java.math.BigDecimal;

import java.util.Locale;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, columnDefinition = "uuid")
    private UUID userKey;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "personal_room_id", length = 32)
    private String personalRoomId;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String email;


    @Column(unique = true)
    private String phone;

    /**
     * 0:any, 1:mobile, 2:web, 3:desktop
     * Matches Step-1R migration: login_type_policy SMALLINT NOT NULL DEFAULT 0
     */
    @Column(name = "login_type_policy", nullable = false)
    private Short loginTypePolicy = 0;

    @Column(name = "active_device_id", length = 128)
    private String activeDeviceId;

    @Column(name = "active_session_id", length = 128)
    private String activeSessionId;


    private String country;
    private String region;
    private String city;

    @Column(precision = 9, scale = 6)       // numeric(9,6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "is_email_verified", nullable = false)
    private boolean isEmailVerified = false;

    @Column(name = "is_phone_verified", nullable = false)
    private boolean isPhoneVerified = false;

    @Column(name = "registration_ip")       // inet
    private String registrationIp;

    @Column(name = "registration_device_id", length = 128)
    private String registrationDeviceId;
    @Column(name = "registration_device_type", length = 32)
    private String registrationDeviceType;

    /**
     * Value can be (ANDROID, IOS, WEB. HARMONYOS)
     */
    private String deviceType;
    
    /**
     * Coming from Apple APN, or Google Firebase
     */
    private String deviceToken;
    
    @Column(name = "role", length = 50)
    private String role;
    
    @Column(name = "tenant_id", nullable = false, columnDefinition = "int default 0")
    private Integer tenantId = 0;

    @PrePersist @PreUpdate
    void normalize() {
        if (username != null)
            username = username.trim().toLowerCase(Locale.ROOT);
        if (email != null)
            email    = email.trim().toLowerCase(Locale.ROOT);
        if (userKey == null) {
            userKey = java.util.UUID.randomUUID();
        }
        
        if (tenantId == null) {
            tenantId = 0;
        }

    }

}

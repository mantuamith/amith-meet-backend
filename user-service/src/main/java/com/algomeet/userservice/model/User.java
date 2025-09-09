package com.algomeet.userservice.model;

import jakarta.persistence.*;
import lombok.*;


import java.math.BigDecimal;

import java.util.Locale;


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

    @Column(unique = true, nullable = false)
    private String username;

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


    @Column(name = "is_email_verified", nullable = false)
    private boolean isEmailVerified = false;

    @Column(name = "is_phone_verified", nullable = false)
    private boolean isPhoneVerified = false;

    @Column(name = "registration_ip")       // inet
    private String registrationIp;

    @PrePersist @PreUpdate
    void normalize() {
        if (username != null) username = username.trim().toLowerCase(Locale.ROOT);
        if (email != null)    email    = email.trim().toLowerCase(Locale.ROOT);
    }

}

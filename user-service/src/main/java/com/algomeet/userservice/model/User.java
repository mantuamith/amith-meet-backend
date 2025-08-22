package com.algomeet.userservice.model;

import jakarta.persistence.*;
import lombok.*;

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

    /**
     * 0:any, 1:mobile, 2:web, 3:desktop
     * Matches Step-1R migration: login_type_policy SMALLINT NOT NULL DEFAULT 0
     */
    @Column(name = "login_type_policy", nullable = false)
    private Short loginTypePolicy = 0;

    @Column(name = "active_device_id", length = 128)
    private String activeDeviceId;
}

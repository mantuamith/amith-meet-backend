package com.algomeet.userservice.model;

import jakarta.persistence.*;
import lombok.*;

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

    @PrePersist @PreUpdate
    void normalize() {
        if (username != null) username = username.trim().toLowerCase(Locale.ROOT);
        if (email != null)    email    = email.trim().toLowerCase(Locale.ROOT);
    }
}

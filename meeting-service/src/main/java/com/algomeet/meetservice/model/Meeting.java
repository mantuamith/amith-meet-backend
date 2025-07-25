package com.algomeet.meetservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
public class Meeting {

    @Id
    private String id;

    private String token;

    private String hostEmail;  // Added field

    private Instant createdAt;

    private Instant expiresAt;
}

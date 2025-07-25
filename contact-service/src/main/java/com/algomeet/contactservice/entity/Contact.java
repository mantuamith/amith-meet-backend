package com.algomeet.contactservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "contacts", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "contactUserId"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String contactUserId;

    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private ContactStatus status = ContactStatus.PENDING;

}
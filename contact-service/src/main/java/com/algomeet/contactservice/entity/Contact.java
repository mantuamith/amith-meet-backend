package com.algomeet.contactservice.entity;

import com.algomeet.contactservice.entity.ContactStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

@Entity
@Table(
        name = "contacts",
        indexes = {
                @Index(name = "idx_contacts_userkey_status", columnList = "user_key,status"),
                @Index(name = "idx_contacts_ctkey_status", columnList = "contact_user_key,status")
        }
)
// Drop the old @UniqueConstraint — DB now enforces order-independent uniqueness via index
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Legacy (keep during transition — may be null in future)
    @Column(name = "user_id")
    private String userId;

    @Column(name = "contact_user_id")
    private String contactUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // New canonical IDs
    @Column(name = "user_key", nullable = false, columnDefinition = "uuid")
    private java.util.UUID userKey;

    @Column(name = "contact_user_key", nullable = false, columnDefinition = "uuid")
    private java.util.UUID contactUserKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContactStatus status = ContactStatus.PENDING;

    @PrePersist
    void prePersist() {
        if (createdAt == null)
            createdAt = Instant.now();
        if (userId != null)
            userId = userId.trim().toLowerCase();
        if (contactUserId != null)
            contactUserId = contactUserId.trim().toLowerCase();
        // ensure UUIDs present
        requireNonNull(userKey, "userKey is required");
        requireNonNull(contactUserKey, "contactUserKey is required");
    }

    @PreUpdate
    void preUpdate() {
        if (userId != null)
            userId = userId.trim().toLowerCase();
        if (contactUserId != null)
            contactUserId = contactUserId.trim().toLowerCase();
    }
}

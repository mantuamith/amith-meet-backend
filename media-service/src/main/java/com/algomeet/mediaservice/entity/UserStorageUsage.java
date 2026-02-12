package com.algomeet.mediaservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_storage_usage")
public class UserStorageUsage {

    /**
     * User identifier.
     * Matches the user service UUID.
     */
    @Id
    @Column(name = "user_key", nullable = false, updatable = false)
    private UUID userKey;

    /* =========================
       Media Storage
       ========================= */

    @Column(name = "media_storage_used", nullable = false)
    private long mediaStorageUsed = 0L;

    @Column(name = "media_file_count", nullable = false)
    private long mediaFileCount = 0L;

    /* =========================
       Chat Storage
       ========================= */

    @Column(name = "chat_storage_used", nullable = false)
    private long chatStorageUsed = 0L;

    @Column(name = "chat_message_count", nullable = false)
    private long chatMessageCount = 0L;

    /* =========================
       Total Storage
       ========================= */

    @Column(name = "total_storage_used", nullable = false)
    private long totalStorageUsed = 0L;

    /* =========================
       Audit
       ========================= */

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.lastUpdated = Instant.now();
    }
}

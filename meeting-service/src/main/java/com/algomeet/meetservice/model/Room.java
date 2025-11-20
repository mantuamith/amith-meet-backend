package com.algomeet.meetservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "rooms")
public class Room {
    @Id @Column(name = "room_id", length = 64)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 16)
    private RoomType roomType;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "lobby_default", nullable = true)
    @Builder.Default
    private boolean lobbyDefault = false;

    @Column(name = "recording_default", nullable = true)
    @Builder.Default
    private boolean recordingDefault = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

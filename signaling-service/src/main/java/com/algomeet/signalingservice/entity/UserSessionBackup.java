package com.algomeet.signalingservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@IdClass(UserSessionBackupId.class)
@Table(name = "user_session_backup")
public class UserSessionBackup {
	/** Composite primary key: userKey + sessionId */
    @Id
    @Column(name = "user_key", nullable = false)
    private UUID userKey;

    @Id
    @Column(name = "session_id", nullable = false, length = 88)
    private String sessionId;
    
    /** User key of remote user **/
    private UUID peerUserKey;
   
    /** true = inbound, false = outbound */
    @Column(nullable = false)
    private boolean inbound;

    /** Base64-encoded AES-encrypted session/pickle */
    @Lob
    @Column(nullable = false)
    private String encryptedSession;
    
    /** Algorithm name, e.g. "OLM" */
    @Column(length = 32)
    private String algorithm;

    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Column(length = 32)
    private String aesAlg;
    
    /** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Column(length = 10)
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Column(length = 88)
    private String salt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    private Instant updatedAt;
        
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    } 
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
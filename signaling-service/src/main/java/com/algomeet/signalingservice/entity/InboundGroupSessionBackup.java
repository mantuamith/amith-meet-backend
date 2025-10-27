package com.algomeet.signalingservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "inbound_group_session_backup")
public class InboundGroupSessionBackup {    
	@EmbeddedId
    private InboundGroupSessionBackupId id;

    @Column(nullable = false)
    private Long groupId;
    
    /** User key of remote user **/
    private UUID peerUserKey;
    
    /** Base64-encoded AES-encrypted session/pickle */
    @Lob
    @Column(nullable = false)
    private String encryptedSession;
    
    /** Algorithm name, e.g. "MEGOLM" */
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

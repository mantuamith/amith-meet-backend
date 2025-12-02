package com.algomeet.signalservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "signal_group_session_backups")
public class GroupSessionBackup {    
	@EmbeddedId
    private GroupSessionBackupId id;
    
	private Integer deviceId;
	
    /** User key of sender address **/
    private UUID senderUserKey;
    
    private Integer senderDeviceId;
        
    /** Base64-encoded AES-encrypted  serialized SenderKeyRecord */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String serializedSession;
            
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

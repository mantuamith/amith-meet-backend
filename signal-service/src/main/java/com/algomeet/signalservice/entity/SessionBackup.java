package com.algomeet.signalservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_session_backups")
public class SessionBackup {
	@EmbeddedId
	private SessionBackupId id;
	  
    /** true = inbound, false = outbound */
    @Column(nullable = false)
    private boolean inbound;

    /** Base64-encoded AES-encrypted serialized session */
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
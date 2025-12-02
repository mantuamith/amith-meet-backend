package com.algomeet.signalservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_group_sender_key_backups")
public class GroupSenderKeyBackup {
	@EmbeddedId
	private GroupSenderKeyBackupId id;
	
	/** Sender key distribution message */
    @Column(nullable = false, length = 300)
	private String serializedSkdm;
    		
    @Column(name = "version", length = 10)
    private String version;

    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Column(name = "aesAlg", length = 32)
    private String aesAlg;

    /** Base64-encoded salt for key derivation. */
    @Column(name = "salt", length = 88)
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

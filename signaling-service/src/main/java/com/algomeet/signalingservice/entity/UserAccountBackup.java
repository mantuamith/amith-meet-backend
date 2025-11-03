package com.algomeet.signalingservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@Entity
@Table(name = "user_account_backup")
public class UserAccountBackup {
	@EmbeddedId
	private UserAccountBackupId id;
	
    /** Base64-encoded AES-encrypted session/pickle */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedAccount;	

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

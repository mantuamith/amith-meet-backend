package com.algomeet.signalingservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
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
@Table(name = "user_keys_backup")
public class UserKeysBackup {
	@Id
	@Column(name = "user_key", nullable = false, updatable = false)
	private UUID userKey;

	// Use TEXT column for long encrypted strings (safe for large ciphertexts)
    @Lob
    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;
	
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
	public UserKeysBackup(UUID userKey, String encryptedPrivateKey) {
		this.userKey = userKey;
		this.encryptedPrivateKey = encryptedPrivateKey;
	}
}

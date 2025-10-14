package com.algomeet.signalingservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "identity_one_time_keys")
public class IdentityOneTimeKey {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "identity_key", nullable = false, length = 255)
    private String identityKey;
    
    @Column(name = "one_time_key", nullable = false, length = 255)
    private String oneTimeKey;
	
    private Instant createdAt;
    private Instant updatedAt;
    
	public IdentityOneTimeKey() {		
	}
	
	public IdentityOneTimeKey(String identityKey, String oneTimeKey) {
		this.identityKey = identityKey;
		this.oneTimeKey = oneTimeKey;
	}
        
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    } 
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

package com.algomeet.signalingservice.entity;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "user_one_time_keys")
public class UserOneTimeKey {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 88)
    private UUID userKey;
    
    @Column(name = "identity_key", nullable = false, length = 88)
    private String identityKey;
    
    @Column(name = "one_time_key", nullable = false, length = 88)
    private String oneTimeKey;
    
    @Column(name = "used", nullable = false)     
    private boolean used = false; 
	
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    private Instant updatedAt;
    
	public UserOneTimeKey() {		
	}
	
	public UserOneTimeKey(UUID userKey, String identityKey, String oneTimeKey) {
		this.userKey = userKey;
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

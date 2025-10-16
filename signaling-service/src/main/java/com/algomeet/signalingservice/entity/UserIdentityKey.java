package com.algomeet.signalingservice.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "user_identity_keys")
public class UserIdentityKey {
	@Id
	@Column(name = "user_key", nullable = false, updatable = false)
	private UUID userKey;

	@Column(name = "identity_key", nullable = false, unique = true, length = 255)
    private String identityKey;
	
	// Join via the identity_key string column
    @OneToMany(
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )
    @JoinColumn(name = "user_key", referencedColumnName = "user_key", insertable = false, updatable = false)
    private List<IdentityOneTimeKey> oneTimeKeys;
	
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

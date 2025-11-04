package com.algomeet.signalingservice.entity;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "user_identity_keys")
public class UserIdentityKey {
	@EmbeddedId
	private UserIdentityKeyId id;
	
	@Column(name = "device_id", nullable = false, length = 88)
	private String deviceId;
	
	// Join via the identity_key string column
    @OneToMany(
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )    
    @JoinColumns({
        @JoinColumn(name = "user_key", referencedColumnName = "user_key", insertable = false, updatable = false),
        @JoinColumn(name = "identity_key", referencedColumnName = "identity_key", insertable = false, updatable = false)
    })
    private List<UserOneTimeKey> oneTimeKeys;
	
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

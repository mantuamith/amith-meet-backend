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
@Table(name = "signal_kyber_pre_keys")
public class KyberPreKey {
	@EmbeddedId
	private KyberPreKeyId id;
	
	@Column(nullable = false)
	private Integer kyberPreKeyId;	
	
	/** Kyber prekey public Key */
    @Column(nullable = false, length = 2100)
	private String publicKey;
	
    /** Kyber prekey signature */
    @Column(nullable = false, length = 200)
	private String signature;
    		
	private Instant createdAt;
    private Instant updatedAt;
	
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

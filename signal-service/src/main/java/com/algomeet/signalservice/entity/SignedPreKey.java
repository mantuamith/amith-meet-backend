package com.algomeet.signalservice.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_signed_pre_keys")
public class SignedPreKey {
	@EmbeddedId
	private SignedPreKeyId id;
	
	private Long signedPreKeyId;
	
	/** Signed prekey public Key */
    @Column(nullable = false, length = 200)
	private String publicKey;
	
    /** Signed prekey signature */
    @Column(nullable = false, length = 200)
	private String signature;
    
    @OneToOne
    @JoinColumns({
        @JoinColumn(name = "userKey", referencedColumnName = "userKey"),
        @JoinColumn(name = "deviceId", referencedColumnName = "deviceId")
    })
    private UserDevice userDevice;
    
    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

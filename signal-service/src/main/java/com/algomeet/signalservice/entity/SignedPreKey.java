package com.algomeet.signalservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_signed_pre_keys")
public class SignedPreKey {
	@EmbeddedId
	private SignedPreKeyId id;
	
	private Integer signedPreKeyId;
	
	/** Signed prekey public Key */
    @Column(nullable = false, length = 200)
	private String publicKey;
	
    /** Signed prekey signature */
    @Column(nullable = false, length = 200)
	private String signature;
		
	private Instant createdAt;
}

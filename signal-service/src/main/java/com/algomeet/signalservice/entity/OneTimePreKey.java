package com.algomeet.signalservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(
		name = "signal_pre_keys",
		indexes = {
				@Index(name = "idx_pre_key", columnList = "userKey, deviceId")
		},
	    uniqueConstraints = {
	            @jakarta.persistence.UniqueConstraint(
	                name = "uc_user_device_prekey",
	                columnNames = { "userKey", "deviceId", "preKeyId" }
	            )
	        }
		)
public class OneTimePreKey {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false)
	private UUID userKey;

	@Column(nullable = false)
	private Integer deviceId;

	@Column(nullable = false)
	private Long preKeyId;

	/** Prekey public Key */
	@Column(nullable = false, length = 200)
	private String publicKey;

	private Boolean used = false;

	private Instant createdAt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
	}
}

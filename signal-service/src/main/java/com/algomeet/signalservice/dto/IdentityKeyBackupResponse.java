package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class IdentityKeyBackupResponse {
	private String deviceId;

	private UUID userKey;
	private Integer registrationId;

	private String serializedIdentityKey;
	private List<String> serializedPreKeys;
	private String serializedSignedPreKey;
	private String serializedKyberPreKey;

	private String aesAlg;
	private String version;
	private String salt;

	private Instant createdAt;
	private Instant updatedAt;
}

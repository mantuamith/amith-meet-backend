package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SessionBackupResponse {
	private UUID userKey;	
	
	private Integer deviceId;

	private Integer registrationId;

	/** remote user's user key **/
	private UUID remoteUserKey;

	/** Remote user's device ID **/
	private Integer remoteDeviceId;

	private String serializedSession;

	/** The encryption algorithm, e.g. "OLM" */
	private String algorithm;	

	/** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
	private String aesAlg;

	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
	private String version;

	/** Base64-encoded salt value for key derivation (optional but recommended). */
	private String salt;

	private Instant createdAt;
	private Instant updatedAt;
}

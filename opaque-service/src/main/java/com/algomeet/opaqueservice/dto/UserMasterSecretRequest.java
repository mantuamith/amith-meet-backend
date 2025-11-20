package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserMasterSecretRequest {
	@NotNull
	private CredentialType type;
	
	@NotEmpty
	private String record;

	@NotEmpty
	private String masterSecretKey;
	
	/** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
	@Size(max = 32)
    private String algorithm;
    
    /** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
	@Size(max = 10)
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
	@Size(max = 88)
    private String salt;
}
package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RetrieveUserMasterSecretRequest {
	@NotNull
	private CredentialType type;
	
	/**
	 * Client auth key
	 */
	@NotEmpty
	private String clientAuth;
}
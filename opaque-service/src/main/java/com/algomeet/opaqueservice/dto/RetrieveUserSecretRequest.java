package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import lombok.Data;

@Data
public class RetrieveUserSecretRequest {
	private CredentialType type;
	
	/**
	 * Client public key
	 */
	private String publicKey;
}
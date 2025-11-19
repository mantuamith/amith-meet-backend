package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import lombok.Data;

@Data
public class LoginRequest {
	private CredentialType type;
	/**
	 * Client public key
	 */
	private String clientPublicKey;
}
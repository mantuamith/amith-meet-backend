package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import lombok.Data;

@Data
public class UserSecretRequest {
	private CredentialType type;
	private String secretKey;
}
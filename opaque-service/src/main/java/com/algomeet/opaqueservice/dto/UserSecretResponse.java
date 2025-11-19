package com.algomeet.opaqueservice.dto;

import java.util.UUID;

import com.algomeet.opaqueservice.enums.CredentialType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSecretResponse {
	private UUID userKey;
	private CredentialType type;
	private String secretKey;
}
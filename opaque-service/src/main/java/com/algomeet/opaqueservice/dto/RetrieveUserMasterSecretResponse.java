package com.algomeet.opaqueservice.dto;

import java.util.UUID;

import com.algomeet.opaqueservice.enums.CredentialType;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RetrieveUserMasterSecretResponse {
	private UUID userKey;
	private CredentialType type;
	private String masterSecretKey;
}
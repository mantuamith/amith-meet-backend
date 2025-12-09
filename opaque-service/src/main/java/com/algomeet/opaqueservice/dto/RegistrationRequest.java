package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrationRequest { 
	@NotNull
	private CredentialType type;
	
	@NotEmpty
	private String clientRegistrationMessage;	
}


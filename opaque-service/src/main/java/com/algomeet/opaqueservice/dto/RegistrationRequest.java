package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import lombok.Data;

@Data
public class RegistrationRequest { 
	private CredentialType type;
	private String clientRegistrationMessageBase64;	
}


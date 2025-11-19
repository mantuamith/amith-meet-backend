package com.algomeet.opaqueservice.dto;

import com.algomeet.opaqueservice.enums.CredentialType;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class FinalizeRegistrationResponse { 
	private CredentialType type;
	private String clientRecord;	
}


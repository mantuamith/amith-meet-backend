package com.algomeet.opaqueservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserCredentialResponse {
	private String serverId;
	private String publicKey;	
}
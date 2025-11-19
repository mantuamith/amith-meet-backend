package com.algomeet.opaqueservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LoginResponse{
	/**
	 * Client public key
	 */
	private String publicKey;
	
	private String serverSecKey;
}
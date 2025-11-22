package com.algomeet.signalservice.dto;

import lombok.Data;

@Data
public class OneTimePreKeyRequest {	
	private Integer preKeyId;
	private String publicKey;
}
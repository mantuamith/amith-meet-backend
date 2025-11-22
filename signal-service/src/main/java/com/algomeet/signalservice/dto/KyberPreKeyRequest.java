package com.algomeet.signalservice.dto;

import lombok.Data;

@Data
public class KyberPreKeyRequest {
	private String publicKey;
	private String signature;
	private Integer kyberPreKeyId;
}


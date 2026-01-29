package com.algomeet.signalservice.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class KyberPreKeyResponse {
	private String userKey;
	private Integer deviceId;
	private String kyberPreKeyId;
	private String publicKey;
	private String signature;
	private Instant createdAt;
    private Instant updatedAt;
}

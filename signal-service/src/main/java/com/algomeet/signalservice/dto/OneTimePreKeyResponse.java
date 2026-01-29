package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class OneTimePreKeyResponse {
	private long id;
	private UUID userKey;
	private Integer deviceId;
	private String preKeyId;
	private String publicKey;
	private Boolean used;
	private Instant createdAt;
}
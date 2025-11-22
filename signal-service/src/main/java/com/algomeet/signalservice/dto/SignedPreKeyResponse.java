package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class SignedPreKeyResponse {
    private UUID userKey;
    private Integer deviceId;
    private Integer signedPreKeyId;
    private String publicKey;
    private String signature;
    private Instant createdAt;
}
package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class UserDeviceResponse {
    private UUID userKey;
    
    private Integer deviceId;
    
    private Integer registrationId;
    
    private String identityKey;    
    
    private SignedPreKeyResponse signedPreKey;
    
    private KyberPreKeyResponse kyberPreKey;    
    
    private List<OneTimePreKeyResponse> oneTimePreKeys;
    
    private Instant createdAt;
    
    private Instant updatedAt;
}

package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class UserDeviceResponse {
    private UUID userKey;
    
    private Integer deviceId;
    
    private Integer registrationId;
    
    private String identityKey;    
    
    private Instant createdAt;
    
    private Instant updatedAt;
}

package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class DeviceKeyResponse {  	
    private UUID userKey;
    
    private Integer deviceId;
    
    private Integer registrationId;
    
    private String identityKey;      
	
    private SignedPreKeyResponse signedPreKey;

    private KyberPreKeyResponse kyberPreKey;
    
    private OneTimePreKeyResponse oneTimePreKey;	
    
    private Instant createdAt;
    
    private Instant updatedAt; 
}
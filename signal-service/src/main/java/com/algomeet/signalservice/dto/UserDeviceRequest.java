package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserDeviceRequest {  
	@NotNull
    private Integer registrationId;
	
	@NotEmpty
    private String identityKey;
	
	@NotNull
    private SignedPreKeyRequest signedPreKey;

    private KyberPreKeyRequest kyberPreKey;
	
}
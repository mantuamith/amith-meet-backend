package com.algomeet.signalservice.dto;

import java.util.List;

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

	@NotNull
    private KyberPreKeyRequest kyberPreKey;
	
	@NotNull
	@NotEmpty
    private List<OneTimePreKeyRequest> oneTimePreKeys;
	
}
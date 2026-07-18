package com.algomeet.signalservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DevicePreKeyBundleRequest {  	
	@NotNull
    private SignedPreKeyRequest signedPreKey;

    private KyberPreKeyRequest kyberPreKey;
	
	@NotNull
	@NotEmpty
    private List<OneTimePreKeyRequest> oneTimePreKeys;	
}
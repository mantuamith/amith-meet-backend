package com.algomeet.signalservice.dto;

import java.util.List;

import lombok.Data;

@Data
public class DevicePreKeyBundleResponse {  	
    private SignedPreKeyResponse signedPreKey;

    private KyberPreKeyResponse kyberPreKey;
    
    private List<OneTimePreKeyResponse> oneTimePreKeys;	
}
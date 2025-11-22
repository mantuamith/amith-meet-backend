package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SignedPreKeyRequest {
	@NotNull
    private Integer signedPreKeyId;
	
	@NotEmpty
    private String publicKey;
	
	@NotEmpty
    private String signature;
}
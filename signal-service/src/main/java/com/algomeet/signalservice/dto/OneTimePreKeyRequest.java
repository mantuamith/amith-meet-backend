package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class OneTimePreKeyRequest {	
	@Min(value = 1, message = "preKeyId must be greater than 0")
	private String preKeyId;
	
	@NotEmpty
	private String publicKey;
}
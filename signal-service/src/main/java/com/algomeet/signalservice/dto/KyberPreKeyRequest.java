package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KyberPreKeyRequest {	
	@NotNull
	@Min(value = 1, message = "kyberPreKeyId must be greater than 0")
	private Integer kyberPreKeyId;
	
	@NotEmpty
	@Size (max = 2100)
	private String publicKey;
	
	@NotEmpty
	private String signature;
}


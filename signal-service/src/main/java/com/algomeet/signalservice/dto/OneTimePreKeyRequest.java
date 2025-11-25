package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class OneTimePreKeyRequest {	
	@Min(value = 0, message = "preKeyId must be greater than equal to 0")
	private Integer preKeyId;
	
	@NotEmpty
	private String publicKey;
}
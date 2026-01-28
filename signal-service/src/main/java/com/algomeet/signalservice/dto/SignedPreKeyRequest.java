package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SignedPreKeyRequest {
	@NotNull
	@Min(value = 1, message = "signedPreKeyId must be greater than 0")
	private String signedPreKeyId;

	@NotEmpty
	private String publicKey;

	@NotEmpty
	private String signature;
}
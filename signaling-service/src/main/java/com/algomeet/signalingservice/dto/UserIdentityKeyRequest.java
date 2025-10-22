package com.algomeet.signalingservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserIdentityKeyRequest {
	@NotEmpty(message = "{identity-key.register.empty-identity-key}")
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "Invalid Base64 format"
		)
	@Size(max = 88, message = "Base64 value too long") // adjust based on expected length
    private String identityKey;
	
	@Size(max = 500, message = "One time key list cannot contain more than 500 keys")	
	@Valid
	private List<@Size(max = 88, message = "Each key must be at most 88 characters long") String> oneTimeKeys;
}
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
			regexp = "^[A-Za-z0-9_-]{43,88}$",
		    message = "{invalid-base64-format}"
		)
	@Size(max = 88, message = "{identity-keyidentity-key-exceeded-max-size}") // adjust based on expected length
    private String identityKey;
	
	@Size(max = 500, message = "{identity-key.one-time-key-list-exceeded-max-size}")	
	@Valid
	private List<@Size(max = 88, message = "{identity-key.one-time-key-exceeded-max-size}") String> oneTimeKeys;
}
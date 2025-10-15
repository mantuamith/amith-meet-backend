package com.algomeet.signalingservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class UserIdentityKeyRequest {
	@NotEmpty(message = "{identity-key.register.empty-identity-key}")
    private String identityKey;
	
	@NotEmpty(message = "{identity-key.register.empty-one-time-keys}")
    private List<String> oneTimeKeys;
}
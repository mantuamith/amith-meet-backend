package com.algomeet.signalingservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserOneTimeKeyRequest {		
	@NotEmpty(message = "{one-time-key.update.empty-one-time-keys}")
	@Size(max = 500, message = "{identity-key.one-time-key-list-exceeded-max-size}")	
	@Valid
    private List<@Size(max = 88, message = "{identity-key.one-time-key-exceeded-max-size}") String> oneTimeKeys;
}
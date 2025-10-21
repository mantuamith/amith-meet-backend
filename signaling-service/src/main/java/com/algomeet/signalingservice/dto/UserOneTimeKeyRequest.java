package com.algomeet.signalingservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class UserOneTimeKeyRequest {	
	@NotEmpty(message = "{one-time-key.update.empty-one-time-keys}")
    private List<String> oneTimeKeys;
}
package com.algomeet.signalservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class OneTimePreKeysRequest {	
	@NotEmpty
	private List<OneTimePreKeyRequest> preKeys;
}
package com.algomeet.signalservice.dto;

import java.util.List;
import lombok.Data;

@Data
public class OneTimePreKeysRequest {	
	private List<OneTimePreKeyRequest> preKeys;
}
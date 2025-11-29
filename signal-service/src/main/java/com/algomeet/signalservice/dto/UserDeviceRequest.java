package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserDeviceRequest {  
	@NotNull
	@Min(value = 1, message = "registrationId must be greater than 0")
    private Integer registrationId;
	
	@NotEmpty
    private String identityKey;	
}
package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserDeviceRequest {  
	@NotNull
	@Min(value = 0, message = "registrationId must be greater than equal to 0")
    private Integer registrationId;
	
	@NotEmpty
    private String identityKey;	
}
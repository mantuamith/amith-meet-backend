package com.algomeet.notificationservice.dto;

import lombok.Data;

@Data
public class UserAuthenticationRequest {
	private String authorization;
	private String deviceToken;
	private String deviceInfo;
}

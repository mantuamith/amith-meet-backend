package com.algomeet.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
	private Long id;
	private String userKey;
	private String username;
	private String email;
	private String clientPlatform;
	private String deviceToken;
}

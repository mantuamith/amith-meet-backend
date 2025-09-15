package com.algomeet.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserAuthInfo {
	private String userKey;
	private String tenantId;
}

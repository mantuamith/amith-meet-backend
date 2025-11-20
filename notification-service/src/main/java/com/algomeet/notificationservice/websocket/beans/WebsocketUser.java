package com.algomeet.notificationservice.websocket.beans;

import java.security.Principal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class WebsocketUser implements Principal {
	private String userKey;
	private Integer tenantId;
	
	@Override
	public String getName() {
		return userKey;
	}
	
}

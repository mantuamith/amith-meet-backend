package com.algomeet.xmpp.chatservice.session;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserSession implements Serializable{
	private static final long serialVersionUID = 1L;
	public UserSession() {}
	
	private String sessionId;
	private boolean isDeviceActive;
}

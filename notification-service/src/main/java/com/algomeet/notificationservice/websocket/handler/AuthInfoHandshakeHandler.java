package com.algomeet.notificationservice.websocket.handler;

import java.security.Principal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import com.algomeet.notificationservice.dto.UserAuthInfo;
import com.algomeet.notificationservice.service.AuthService;
import com.algomeet.notificationservice.util.JwtUtil;
import com.algomeet.notificationservice.websocket.beans.WebsocketUser;

@Component
public class AuthInfoHandshakeHandler extends DefaultHandshakeHandler {
	@Autowired
	private AuthService authService;

	@Override
	protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
		Principal principal = null;
		
		// Get client user/ subscriber info
		UserAuthInfo userAuthInfo = authService.getAuthInfo(JwtUtil.getAutorizationToken(request));
		principal = new WebsocketUser(userAuthInfo.getUserKey(), userAuthInfo.getTenantId());

		return principal;
	}  

	public void setSupportedProtocols(String... protocols) {
		super.setSupportedProtocols(protocols);
	}
}
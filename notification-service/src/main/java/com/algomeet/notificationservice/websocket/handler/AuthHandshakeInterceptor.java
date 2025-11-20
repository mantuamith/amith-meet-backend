package com.algomeet.notificationservice.websocket.handler;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.util.JwtUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
	@Autowired
	private AuthInfoHandshakeHandler handshakeHandler;
	
	@Autowired
	private JwtUtil jwtUtil;
	
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {
        log.info("Intercept handshake: " + request.getURI());
        
        List<String> protocols = request.getHeaders().get(Constants.SEC_WEBSOCKET_PROTOCOL);

        // Validate token
        boolean isValidToken = jwtUtil.validate(JwtUtil.getAutorizationToken(request));

        if (isValidToken && !CollectionUtils.isEmpty(protocols)) {
        	// Used request header protocol attribute to hold the user auth.
        	String authorization = protocols.get(0);
        		
	       	// Set supported protocol so that response header contain supported protocol will be sent back to the client
        	handshakeHandler.setSupportedProtocols(authorization);        	
        }
        
        return isValidToken; // false = reject
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception ex) {
    }
}

package com.algomeet.notificationservice.websocket;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.dto.UserAuthenticationRequest;
import com.algomeet.notificationservice.service.AuthService;
import com.algomeet.notificationservice.util.MessageUtil;
import com.algomeet.notificationservice.util.WebSocketMessageUtil;
import com.algomeet.notificationservice.websocket.processor.WebSocketMessageProcessor;
import com.algomeet.notificationservice.websocket.processor.WebSocketMessageProcessorProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler { 
	private static final Set<WebSocketSession> unauthenticatedSessions = new CopyOnWriteArraySet<>();
	private static final Map<String, Set<WebSocketSession>> authenticatedUserSessions = new ConcurrentHashMap<>();
	
	@Autowired
	private AuthService authService;
	
	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	private WebSocketMessageProcessorProvider messageProcessorProvider;

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {  
		session.getAttributes().put(Constants.SESSION_ATTR_TIME_CONNECTED, System.currentTimeMillis());
		unauthenticatedSessions.add(session);

		log.info("New connection established: " + session.getId());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		removeSession(session);
		log.info("Connection closed: " + session.getId() + " Reason: " + status);
	}

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {    	
		String payload = message.getPayload();    		
		// Handle first message client authentication
		if (unauthenticatedSessions.contains(session)) {
			boolean isAuthenticated = false;
			
			try {
				if (WebSocketMessageUtil.isAuthMessage(payload)) {					
					UserAuthenticationRequest authRequest = objectMapper.readValue(payload, UserAuthenticationRequest.class);
					log.info("Auth payload: {}", authRequest);

					String username = authService.getUsername(authRequest.getAuthorization());
					if (StringUtils.hasText(username)) {						

						Set<WebSocketSession> userSessions = authenticatedUserSessions.getOrDefault(username, new CopyOnWriteArraySet<>());
						userSessions.add(session);	
						authenticatedUserSessions.put(username, userSessions);

						session.getAttributes().put(Constants.SESSION_ATTR_USERNAME, username.trim());	
						if (StringUtils.hasText(authRequest.getDeviceToken())){
							session.getAttributes().put(Constants.SESSION_ATTR_DEVICE_TOKEN, authRequest.getDeviceToken().trim());
						}
						isAuthenticated = true;
					} 
				}
			} catch(Exception ex) {}	

			if (!isAuthenticated) {
				session.sendMessage(new TextMessage(
						MessageUtil.getMessage("unauthorizedAccess")));
				// Terminate user session
				session.close();
			}
			removeFromUnauthenticatedSessions(session);
			
			return;
		} 

		// Process received message
		processReceivedMesssage(payload);
		
		log.info("Received message: " + payload);
	}
	
	private void processReceivedMesssage(String payload) {
		if (!(StringUtils.hasLength(payload))) {
			return;
		}
		
    	// Retrieve websocket message processors
    	List<WebSocketMessageProcessor> processors = messageProcessorProvider.getProcessors();
    	for (WebSocketMessageProcessor processor : processors) {
    		if (processor.doProcess(payload)) {
    			// Message processed 
    			break;
    		}
    	}
	}
	
	public static Set<WebSocketSession> getUnauthenticatedsessions() {
		return unauthenticatedSessions;
	}   
	
	public static Map<String, Set<WebSocketSession>> getAuthenticatedUserSessions() {
		return authenticatedUserSessions;
	}   
	
	public static void removeFromUnauthenticatedSessions(WebSocketSession session) {
		unauthenticatedSessions.remove(session);
	}
	
	public static void removeFromAuthenticatedSessions(WebSocketSession session) {
		String username = (String) session.getAttributes().get(Constants.SESSION_ATTR_USERNAME);
		
		if (Objects.nonNull(username)) {
			authenticatedUserSessions.get(username).remove(session);
		}
	}
	
	public static void removeSession(WebSocketSession session) {
		removeFromUnauthenticatedSessions(session);
		removeFromAuthenticatedSessions(session);
	}	
}
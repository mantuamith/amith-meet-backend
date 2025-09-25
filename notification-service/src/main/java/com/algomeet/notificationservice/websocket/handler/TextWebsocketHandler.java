package com.algomeet.notificationservice.websocket.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.consumer.processor.PushNotificationProcessor;
import com.algomeet.notificationservice.dto.ExchangeMessage;
import com.algomeet.notificationservice.dto.NotificationMessage;
import com.algomeet.notificationservice.dto.UserNotificationDto;
import com.algomeet.notificationservice.enums.MessageType;
import com.algomeet.notificationservice.service.UserNotificationService;
import com.algomeet.notificationservice.util.UserNotificationUtil;
import com.algomeet.notificationservice.websocket.beans.WebsocketUser;
import com.algomeet.notificationservice.websocket.processor.WebSocketMessageProcessor;
import com.algomeet.notificationservice.websocket.processor.WebSocketMessageProcessorProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TextWebsocketHandler extends TextWebSocketHandler { 
	private static final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

	@Autowired
	private WebSocketMessageProcessorProvider messageProcessorProvider;

	@Autowired
	private UserNotificationService userNotificationService;

	@Autowired
	private PushNotificationProcessor pushNotificationProcessor;

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {  
		WebsocketUser websocketUser = (WebsocketUser) session.getPrincipal();
		
		// Add to sessions map	
		Set<WebSocketSession> websocketSessions = sessions.getOrDefault(websocketUser.getUserKey(), new HashSet<>());
		websocketSessions.add(session);
		
		// Check if user not added yet
		if (!sessions.containsKey(websocketUser.getUserKey())) {
			sessions.put(websocketUser.getUserKey(), websocketSessions);
		}

		// Push un-delivered notifications
		pushUndeliveredNotifications(websocketUser.getUserKey());

		log.info("New connection established Session Id: " + session.getId() + " User key: " + websocketUser.getUserKey());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		removeSession(session);
		log.info("Connection closed: " + session.getId() + " Reason: " + status);
	}

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {    	
		String payload = message.getPayload();    	
		log.info("New Message: " + session.getId());

		// Process received message
		processReceivedMesssage(session, payload);

		log.info("Received message: " + payload);
	}

	private void processReceivedMesssage(WebSocketSession session, String payload) {
		if (!(StringUtils.hasLength(payload))) {
			return;
		}

		WebsocketUser websocketUser = (WebsocketUser) session.getPrincipal();

		// Switch db schema
		TenantContext.switchTenantExplicitly(websocketUser.getTenantId());		
		ExchangeMessage exchangeMessage = null; 
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			exchangeMessage = objectMapper.readValue(payload, ExchangeMessage.class);
		} catch(Exception ex) {}
		
		log.info("Exchange: {}", exchangeMessage);
		if (exchangeMessage == null) {
			return;
		}
		
		// Retrieve websocket message processors
		Map<MessageType, WebSocketMessageProcessor> processors = messageProcessorProvider.getProcessors();		
		if (processors.containsKey(exchangeMessage.getType())){		
			// Process message
			processors.get(exchangeMessage.getType()).doProcess(session, payload, exchangeMessage.getType());
		}

		// Cleanup
		TenantContext.clear();
	}

	private void pushUndeliveredNotifications(String userKey) {
		List<UserNotificationDto> list = userNotificationService.getUndeliveredNotifications(userKey);

		for (UserNotificationDto userNotifDto : list) {
			// Set delivery acknowledge to required
			userNotifDto.getNotification().setDeliveryAckRequired(true);
			UserNotificationUtil.addNotificationCustomData(userNotifDto.getNotification(), userNotifDto);

			//Push notification message
			pushNotificationProcessor.pushMessage(userKey, 
					NotificationMessage.getNotificationMessage(userNotifDto.getNotification()));
		}		
	}

	public static Map<String, Set<WebSocketSession>> getSessions() {
		return sessions;
	}   

	public static void removeFromSessions(WebSocketSession session) {
		WebsocketUser websocketUser = (WebsocketUser) session.getPrincipal();
		
		if (Objects.nonNull(websocketUser.getUserKey())) {
			sessions.get(websocketUser.getUserKey()).remove(session);

			if (sessions.get(websocketUser.getUserKey()).size() == 0) {
				sessions.remove(websocketUser.getUserKey());
			}
		}
	}

	public static void removeSession(WebSocketSession session) {
		removeFromSessions(session);
	}	
}
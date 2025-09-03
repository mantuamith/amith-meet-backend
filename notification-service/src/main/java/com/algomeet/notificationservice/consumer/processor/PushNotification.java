package com.algomeet.notificationservice.consumer.processor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.consumer.receiver.processor.ReceiverGroupProcessor;
import com.algomeet.notificationservice.consumer.receiver.processor.ReceiverGroupProcessorProvider;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.NotificationMessage;
import com.algomeet.notificationservice.dto.UserDto;
import com.algomeet.notificationservice.model.Notification;
import com.algomeet.notificationservice.model.UserNotification;
import com.algomeet.notificationservice.repository.UserNativeRepository;
import com.algomeet.notificationservice.repository.UserNotificationRepository;
import com.algomeet.notificationservice.service.ApnsSenderService;
import com.algomeet.notificationservice.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PushNotification implements NotificationProcessor{
	@Autowired
	private UserNotificationRepository userNotificationRepository;

	@Autowired
	private UserNativeRepository userNativeRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ReceiverGroupProcessorProvider receiverGroupProcessorProvider;

	@Autowired
	private ApnsSenderService apnsSenderService;

	@Override
	public int getOrder() {		
		return 1;
	} 

	@Override
	public void doProcess(NotificationDto notification) {
		if (CollectionUtils.isEmpty(notification.getReceiverIds())
				&& Objects.isNull(notification.getReceiverGroup())) {
			return;
		}

		Set<UserDto> receiverList = new HashSet<>();	
		if (!CollectionUtils.isEmpty(notification.getReceiverIds())) {
			List<UserDto> users = getUserReceiverList(notification.getReceiverIds());

			if (!CollectionUtils.isEmpty(users)) {
				receiverList.addAll(users);
			}
		}

		// Retrieve notification receiver group processors
		List<ReceiverGroupProcessor> processors = receiverGroupProcessorProvider.getProcessors();
		for (ReceiverGroupProcessor processor : processors) {
			List<UserDto> userList = processor.getUserList(notification);

			if (!(CollectionUtils.isEmpty(userList))) {
				receiverList.addAll(userList);
			}
		}  

		for (UserDto userDto : receiverList) {	
			try {				
				// Check if user has the notification already
				if (isUserNotificationExists(userDto.getId(),  notification.getId())) { 
					continue;
				}
				
				// Save user notification
				UserNotification userNotification = saveUserNotification(userDto.getId(), notification);

				// Add metadata such as user notification id, and notification type
				addNotificationCustomData(notification, userNotification);

				// Push notification
				if (StringUtils.hasLength(userDto.getDeviceToken())) {
					// Apple device client
					try {
						boolean deliveryStatus = apnsSenderService.sendPush(userDto.getDeviceToken(), notification);
						// Update status
						updateDeliveryStataus(deliveryStatus, userNotification);

					} catch (Exception e) {}

				} else {
					// Android device or web client

					Set<WebSocketSession> userSessions = NotificationWebSocketHandler.getAuthenticatedUserSessions()
							.get(userDto.getUsername());

					if(!(CollectionUtils.isEmpty(userSessions))) {
						for (WebSocketSession session : userSessions) {
							send(session, notification);
						}
					}	
				}
			} catch(Exception ex) {
				log.error("Error sending notification {}", ex.getMessage(), ex);
			}
		}
	}

	private void addNotificationCustomData(NotificationDto dto, UserNotification userNotification) {	
		if (Objects.isNull(dto.getData())) {
			dto.setData(new HashMap<>());
		}

		if (Objects.nonNull(userNotification)) {
			dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_NOTIFICATION_ID, userNotification.getId());
		}

		dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_NOTIFICATION_TYPE, dto.getType());
		dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_DELIVERY_ACK_REQUIRED, dto.isDeliveryAckRequired());

	}

	private void updateDeliveryStataus(boolean isDelivered, UserNotification userNotification) {
		if (Objects.isNull(userNotification)) {
			return;
		}

		if (isDelivered) {
			userNotification.setDelivered(true);
			userNotificationRepository.save(userNotification);
		}
	}

	private UserNotification saveUserNotification(Long userId, NotificationDto dto) {
		if (Objects.isNull(dto) || !(dto.isDeliveryAckRequired())) {
			return null;
		}

		UserNotification userNotification = new UserNotification();
		userNotification.setUserId(userId);

		Notification notification = new Notification();
		notification.setId(dto.getId());

		userNotification.setNotification(notification);

		return userNotificationRepository.save(userNotification);
	}
	
	private boolean isUserNotificationExists(long userId, UUID notificationId) {
		return !(CollectionUtils.isEmpty(userNotificationRepository
				.findByUserIdAndNotification_Id(userId, notificationId)));
	}

	private List<UserDto> getUserReceiverList(Set<String> usernameList) {
		if (CollectionUtils.isEmpty(usernameList)) {
			return List.of();
		}

		return userNativeRepository.getUsersByUsernameList(usernameList.stream().toList());

	}

	private void send(WebSocketSession session, NotificationDto notification) {
		try {
			ObjectWriter ow = objectMapper.writer().withDefaultPrettyPrinter();
			String jsonMessage = ow.writeValueAsString(NotificationMessage.getNotificationMessage(notification));

			session.sendMessage(new TextMessage(jsonMessage));			
		} catch (Exception ex) {
			log.error("Error sending notification {}", ex.getMessage(), ex);
		}
	}
}

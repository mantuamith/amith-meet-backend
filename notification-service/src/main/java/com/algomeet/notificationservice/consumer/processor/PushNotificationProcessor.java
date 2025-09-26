package com.algomeet.notificationservice.consumer.processor;

import java.util.ArrayList;
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

import com.algomeet.notificationservice.consumer.receiver.processor.ReceiverGroupProcessor;
import com.algomeet.notificationservice.consumer.receiver.processor.ReceiverGroupProcessorProvider;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.NotificationMessage;
import com.algomeet.notificationservice.dto.PublishPushMessageDto;
import com.algomeet.notificationservice.dto.UserDto;
import com.algomeet.notificationservice.enums.DeviceType;
import com.algomeet.notificationservice.model.Notification;
import com.algomeet.notificationservice.model.UserNotification;
import com.algomeet.notificationservice.publisher.PushMessagePublisher;
import com.algomeet.notificationservice.repository.UserNativeRepository;
import com.algomeet.notificationservice.repository.UserNotificationRepository;
import com.algomeet.notificationservice.service.ApnsSenderService;
import com.algomeet.notificationservice.util.UserNotificationUtil;
import com.algomeet.notificationservice.websocket.handler.TextWebsocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PushNotificationProcessor implements NotificationProcessor{
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
	
	@Autowired
	private PushMessagePublisher pushMessagePublisher;

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
				if (isUserNotificationExists(UUID.fromString(userDto.getUserKey()),  notification.getId())) { 
					continue;
				}
				
				// Save user notification
				UserNotification userNotification = saveUserNotification(userDto.getUserKey(), notification);

				// Add metadata such as user notification id, and notification type
				UserNotificationUtil.addNotificationCustomData(notification, userNotification);

				// Push notification
				if (DeviceType.IOS.name().equals(userDto.getDeviceType())
						&& StringUtils.hasLength(userDto.getDeviceToken())) {
					// Apple device client
					try {
						boolean deliveryStatus = apnsSenderService.sendPush(userDto.getDeviceToken(), notification);
						// Update status
						updateDeliveryStataus(deliveryStatus, userNotification);

					} catch (Exception e) {}
				} else {
					// Android and web clients
					NotificationMessage notifMessage = NotificationMessage.getNotificationMessage(notification);
					ObjectWriter ow = objectMapper.writer().withDefaultPrettyPrinter();
					String jsonMessage = ow.writeValueAsString(new PublishPushMessageDto(userDto.getUserKey(), notifMessage));
					
					// Publish push message to all subscribers to support multiple running instances of notification-service
					pushMessagePublisher.publish(jsonMessage);
				}
			} catch(Exception ex) {
				log.error("Error sending notification {}", ex.getMessage(), ex);
			}
		}
	}
	
	public void pushMessage(String userKey, NotificationMessage notifMessage) {
		// Android devices or web clients
		Set<WebSocketSession> subcriberSessions = TextWebsocketHandler.getSessions()
				.get(userKey);

		if(!(CollectionUtils.isEmpty(subcriberSessions))) {
			for (WebSocketSession session : subcriberSessions) {
				send(session, notifMessage);
			}
		}	
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

	private UserNotification saveUserNotification(String userKey, NotificationDto notificationDto) {
		if (Objects.isNull(notificationDto) || !(notificationDto.isDeliveryAckRequired())) {
			return null;
		}

		UserNotification userNotification = new UserNotification();
		userNotification.setUserKey(UUID.fromString(userKey));

		Notification notification = new Notification();
		notification.setId(notificationDto.getId());
		userNotification.setNotification(notification);

		UserNotification saved = userNotificationRepository.save(userNotification);
		
		return saved;
	}
	
	private boolean isUserNotificationExists(UUID userKey, UUID notificationId) {
		return !(CollectionUtils.isEmpty(userNotificationRepository
				.findByUserKeyAndNotification_Id(userKey, notificationId)));
	}

	private List<UserDto> getUserReceiverList(Set<String> rawReceiverList) {
		if (CollectionUtils.isEmpty(rawReceiverList)) {
			return List.of();
		}
        
		// Compatible for user name
		List<String> usernames = new ArrayList<String>();
		List<String> userKeys = new ArrayList<String>();
		
		for (String receiver : rawReceiverList) {
			try {
				UUID.fromString(receiver);
				userKeys.add(receiver);
			} catch(Exception ex) {
				usernames.add(receiver);
			}
		}
		
		List<UserDto> receiverList = new ArrayList<UserDto>();
		if (!CollectionUtils.isEmpty(userKeys)) {
			receiverList.addAll(userNativeRepository.getUsersByUserKeyList(rawReceiverList.stream().toList()));
		} 
		
		if (!CollectionUtils.isEmpty(usernames)) {
			receiverList.addAll(userNativeRepository.getUsersByUsernameList(rawReceiverList.stream().toList()));
		} 
		
		return receiverList;
	}

	private void send(WebSocketSession session, NotificationMessage notificationMessage) {
		try {			
			ObjectWriter ow = objectMapper.writer().withDefaultPrettyPrinter();
			String jsonMessage = ow.writeValueAsString(notificationMessage);

			session.sendMessage(new TextMessage(jsonMessage));			
		} catch (Exception ex) {
			log.error("Error sending notification {}", ex.getMessage(), ex);
		}
	}
}

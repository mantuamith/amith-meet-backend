package com.algomeet.notificationservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Notification {
	private UUID id;
	private NotificationType type;
	private String title;
	private String body;
	private String senderId;	
	/**
	 * Specify manually the list of receiver users username
	 */
	private Set<String> receiverIds;
	/**
	 * Used to send notfication to a group of users
	 */
	private ReceiverGroup receiverGroup;
	/**
	 * Used to store the ID related to the receiver group, which will help finding the list of receiver users.
	 */
	private String receiverGroupRefId;
	/**
	 * Used for sending custom or additional information regarding the notification
	 */
	private Map<String, Object> data;
	/**
	 * Set the value to true if you want offline users to receive the message once they are back online
	 */
	private boolean deliveryAckRequired;
	private Instant expiredAt;	
	private Instant createdAt;
}

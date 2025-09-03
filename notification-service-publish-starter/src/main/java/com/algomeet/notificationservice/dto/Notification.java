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

	private Set<String> receiverIds;
	private ReceiverGroup receiverGroup;
	private String receiverGroupRefId;
	
	private Map<String, Object> data;
	/**
	 * Set the value to true if you want offline users to receive the message once they are back online
	 */
	private boolean deliveryAckRequired;
	private Instant expiredAt;	
	private Instant createdAt;
}

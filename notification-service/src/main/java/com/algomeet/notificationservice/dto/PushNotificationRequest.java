package com.algomeet.notificationservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushNotificationRequest {
	@Pattern(
		    regexp = "|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
		    message = "{push-notification.id.invalid}"
		)
	@Size(max = 64)
    private String id;
	
	@NotNull(message = "{push-notification.type.isBlank}")
    private NotificationType type;
	
	@Size(max = 255)
    private String title;
	@Size(max = 2000)
    private String body;
	@Size(max = 32)
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
	@Size(max = 32)
	private String receiverGroupRefId;
	/**
	 * Used for sending custom or additional information regarding the notification
	 */
	private Map<String, Object> data;
	/**
	 * Set the value to true if you want offline users to receive the message once they are back online
	 */
	private boolean deliveryAckRequired;
	
    private Instant createdAt;
    private Instant expiredAt;   
    
	/**
	 * Used for tenant schema identifier.Notification processing used redis stream queue which 
	 * consumed by workers that running different threads.
	 */
	private Integer tenantId;
}
package com.algomeet.notificationservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PushNotificationRequest {
	@Pattern(
		    regexp = "|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
		    message = "{push-notification.id.invalid}"
		)
    private String id;
	
	@NotNull(message = "{push-notification.type.isBlank}")
    private NotificationType type;
    private String title;
    private String body;
    private String senderId;
    private Set<String> receiverIds;
    private ReceiverGroup receiverGroup;
    private String receiverGroupRefId;
    private Instant createdAt;
    private Instant expiredAt;
    private Map<String, Object> data;
	private boolean deliveryAckRequired;
    
}
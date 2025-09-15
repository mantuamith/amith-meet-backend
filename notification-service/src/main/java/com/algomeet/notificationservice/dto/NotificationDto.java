package com.algomeet.notificationservice.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class NotificationDto {
    private UUID id;
    private String type;
    private String title;
    private String body;
    private String senderId;
    private Set<String> receiverIds;
    private String receiverGroup;
    private String receiverGroupRefId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiredAt;
    private Map<String, Object> data;
	private boolean deliveryAckRequired;
	
	/**
	 * Used for tenant schema identifier.Notification processing used redis stream queue which 
	 * consumed by workers that running different threads.
	 */
	private Integer tenantId;
}
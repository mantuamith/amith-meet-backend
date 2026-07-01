package com.algomeet.xmpp.chatservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinMucMessageRequest {
	@NotNull(message = "Group Id cannot be blank")
	private UUID groupId;

	@NotNull(message = "Message ID is required")
	private UUID messageId;

	@NotNull(message = "Pinned By user ID is required")
	private UUID pinnedBy;

	private boolean pinnedForEveryone;

	private Integer expirationHours;
}

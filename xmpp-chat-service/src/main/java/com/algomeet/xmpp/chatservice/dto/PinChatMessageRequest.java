package com.algomeet.xmpp.chatservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinChatMessageRequest {
	@NotNull(message = "Message ID is required")
	private UUID messageId;

	@NotNull(message = "Pinned By user ID is required")
	private UUID pinnedBy;
	
	@NotBlank(message = "The unique session ID of the connected user. It is returned after user successfully connected to chat websocket.")
	private String sessionId;

	private boolean pinnedForEveryone;

	private Integer expirationHours;
}

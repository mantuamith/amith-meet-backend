package com.algomeet.xmpp.chatservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinConversationRequest {
	private UUID peerKey;
	
	private UUID groupId;
	
	@NotBlank(message = "The unique session ID of the connected user. It is returned after user successfully connected to chat websocket.")
	private String sessionId;

	private Integer expirationHours;
}

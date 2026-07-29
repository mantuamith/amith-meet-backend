package com.algomeet.xmpp.chatservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConversationPreferenceRequest {
	private UUID peerKey;
	
	private UUID groupId;
	
	@NotBlank(message = "The unique session ID of the connected user. It is returned after user successfully connected to chat websocket.")
	private String sessionId;
}

package com.algomeet.xmpp.chatservice.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinConversationResponse {
	private UUID peerKey;	
	private UUID groupId;	

	private UUID seq;
	private Long expiration;
	private Long createdAt;
}

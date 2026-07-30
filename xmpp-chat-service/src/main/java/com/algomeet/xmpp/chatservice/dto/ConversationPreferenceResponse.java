package com.algomeet.xmpp.chatservice.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationPreferenceResponse {
	private UUID peerKey;	
	private UUID groupId;
		
	private Boolean pinned;
	private UUID pinnedSeq;
	private Long pinnedAt;   

	private Boolean muted;       
	private Long muteUntil;

	private Boolean archived;
	private Long archivedAt;

	private Long createdAt;
	
	private Long updatedAt;
}

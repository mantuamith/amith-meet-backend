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
public class PinChatMessageResponse {
	private UUID peerKey;
	private UUID messageId;
	private UUID pinnedBy;
	private UUID seq;
	private boolean pinnedForEveryone;
	private Long expiration;
	private Long createdAt;
}

package com.algomeet.chatservice.registry;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionMetadata {
	private String sessionId;
	private boolean isActive;
}

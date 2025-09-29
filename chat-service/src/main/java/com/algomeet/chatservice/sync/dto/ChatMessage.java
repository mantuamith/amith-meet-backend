package com.algomeet.chatservice.sync.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessage {
	private String user;
	private String destination;
	private Object payload;
}

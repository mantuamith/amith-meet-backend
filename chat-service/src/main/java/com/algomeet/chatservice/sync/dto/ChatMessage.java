package com.algomeet.chatservice.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
	private String to;
	private String destination;
	private Object payload;
	private String from;
}

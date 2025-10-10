package com.algomeet.chatservice.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class SessionMetadata implements Serializable{
	private static final long serialVersionUID = 1L;
	public SessionMetadata() {		
	}
	
	private String sessionId;
	private boolean isActive;
}

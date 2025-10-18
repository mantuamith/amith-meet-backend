package com.algomeet.signalingservice.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class GroupSessionRequest implements Serializable{
	private static final long serialVersionUID = 1L;
	private Long groupChatId;
	
	/**
	 * Out bound group session ID
	 */
	private String sessionId;
	
	/**
	 * Out bound group session key
	 */
	private String sessionKey;
	
	/**
	 * Out bound group session
	 */
	private String encryptedSession;
	
	private List<InboundGroupSessionKey> inboundGroupSessionKeys;
}

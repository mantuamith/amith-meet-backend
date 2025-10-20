package com.algomeet.signalingservice.dto;

import java.io.Serializable;
import java.util.UUID;

import lombok.Data;

@Data
public class InboundGroupSessionKey implements Serializable{
	private static final long serialVersionUID = 1L;
	private UUID userKey;
	private String sessionId;
	private String encryptedSessionKey;
}

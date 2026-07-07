package com.algomeet.xmpp.chatservice.dto;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class MucMessageResponse {
	private UUID stanzaId;         

	private UUID messageId; 

	private UUID roomId;

	private UUID from;
	private UUID to;
	private String stanzaXml;
	private Long deletedAt;
	
	private Long readAt;

	private Boolean isHidden = false;

	private List<UUID> readByIds;
	private Long createdAt;
	private Long expireAt;
}

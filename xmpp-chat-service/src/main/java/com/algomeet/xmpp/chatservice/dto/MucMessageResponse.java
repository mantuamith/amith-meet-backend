package com.algomeet.xmpp.chatservice.dto;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class MucMessageResponse {
	private UUID stanzaId;         

	private UUID messageId; 

	private UUID roomId;

	private UUID from;
	private UUID to;
	private String stanzaXml;
	private Instant deletedAt;
	
	private Instant readAt;

	@JsonIgnore
	private Set<UUID> hiddenFromUserKeys = new HashSet<>();
	private Boolean isHidden = false;
	private Boolean startOfRoomConversation = false;

	private List<UUID> readByIds;
	private Instant createdAt;
	private Instant expireAt;
}

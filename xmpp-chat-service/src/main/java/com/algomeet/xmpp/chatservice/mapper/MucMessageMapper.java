package com.algomeet.xmpp.chatservice.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.repository.projection.MucMessageView;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

@Component
public class MucMessageMapper {

	public MucMessageResponse toResponse(MucMessage document, UUID userKey) {
		if (document == null) {
			return null;
		}

		MucMessageResponse response = new MucMessageResponse();

		// Map structural IDs (Note explicit id -> stanzaId transition)
		response.setStanzaId(document.getId());
		response.setMessageId(document.getMessageId());
		response.setRoomId(document.getRoomId());
		response.setFrom(document.getFrom());
		response.setTo(document.getTo());

		// Map payload payloads
		response.setStanzaXml(document.getStanzaXml());
		response.setDeletedAt(document.getDeletedAt());
		response.setReadAt(document.getReadAt());

		System.out.println("--------->" + userKey);
		if (document.getHiddenFromUserKeys() != null
				&& document.getHiddenFromUserKeys().contains(
					userKey)) {
				response.setIsHidden(true);
		}

		response.setStartOfRoomConversation(document.getStartOfRoomConversation());
		if(document.getCreatedAt() != null) {
			response.setCreatedAt(document.getCreatedAt().toEpochMilli());
		}
		
		if(document.getExpireAt() != null) {
			response.setExpireAt(document.getExpireAt().toEpochMilli());
		}

		return response;
	}
	
	public MucMessageResponse toResponse(MucMessageView summary) {
		if (summary == null) {
			return null;
		}

		MucMessageResponse response = new MucMessageResponse();

		// Map structural IDs (Note explicit id -> stanzaId transition)
		response.setStanzaId(summary.getId());
		response.setMessageId(summary.getMessageId());
		response.setRoomId(summary.getRoomId());
		response.setFrom(summary.getFrom());
		response.setTo(summary.getTo());

		// Map payload payloads
		response.setDeletedAt(summary.getDeletedAt());
		response.setReadAt(summary.getReadAt());

		// Safe collection mapping to prevent sharing internal mutable references
		if (summary.getHiddenFromUserKeys() != null
				&& summary.getHiddenFromUserKeys().contains(
					UUID.fromString(SecurityUtil.getUserKey()))) {
				response.setIsHidden(true);
		}

		if(summary.getCreatedAt() != null) {
			response.setCreatedAt(summary.getCreatedAt().toEpochMilli());
		}

		return response;
	}
}
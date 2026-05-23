package com.algomeet.xmpp.chatservice.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;

@Component
public class MucMessageMapper {

	public MucMessageResponse toResponse(MucMessage document) {
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

		// Safe collection mapping to prevent sharing internal mutable references
		if (document.getHiddenFromUserKeys() != null) {
			response.setHiddenFromUserKeys(
					document.getHiddenFromUserKeys());
		}

		response.setStartOfRoomConversation(document.getStartOfRoomConversation());
		response.setCreatedAt(document.getCreatedAt());
		response.setExpireAt(document.getExpireAt());

		return response;
	}

	public List<MucMessageResponse> toResponseList(List<MucMessage> documents) {
		if (documents == null) {
			return null;
		}

		List<MucMessageResponse> responses = new ArrayList<>(documents.size());
		for (MucMessage doc : documents) {
			responses.add(toResponse(doc));
		}
		return responses;
	}
}
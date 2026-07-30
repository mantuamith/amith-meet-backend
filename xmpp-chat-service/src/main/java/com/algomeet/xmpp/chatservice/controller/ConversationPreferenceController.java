package com.algomeet.xmpp.chatservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.controller.doc.ConversationPreferenceControllerDoc;
import com.algomeet.xmpp.chatservice.document.ConversationPreference;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.ConversationPreferenceResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.ConversationPreferenceService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/conversations")
public class ConversationPreferenceController implements ConversationPreferenceControllerDoc{
	private final ConversationPreferenceService conversationPreferenceService;

	@GetMapping("/preferences")
	public Mono<ResponseEntity<CommonResponse<List<ConversationPreferenceResponse>>>> getPreferences() {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey()); 
		return conversationPreferenceService.getConversationPreferences(userKey)
				.map(m -> mapToResponse(m))
				.collectList()
				.map(list -> ResponseEntity
						.ok()
						.body(CommonResponse.from(ResponseCode.SUCCESS, list)));
	}
		
	/**
	 * Maps the internal domain document into the Long/Epoch-based response DTO format.
	 */
	private ConversationPreferenceResponse mapToResponse(ConversationPreference doc) {
	    return ConversationPreferenceResponse.builder()
	            .peerKey(doc.getPeerKey())
	            .groupId(doc.getGroupId())
	            .pinned(doc.getPinned())
	            .pinnedSeq(doc.getPinnedSeq())
	            .pinnedAt(doc.getPinnedAt() != null ? doc.getPinnedAt().toEpochMilli() : null)
	            .muted(doc.getMuted())
	            .muteUntil(doc.getMuteUntil() != null ? doc.getMuteUntil().toEpochMilli() : null)
	            .archived(doc.getArchived())
	            .archivedAt(doc.getArchivedAt() != null ? doc.getArchivedAt().toEpochMilli() : null)
	            .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toEpochMilli() : null)
	            .updatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toEpochMilli() : null)
	            .build();
	}
}
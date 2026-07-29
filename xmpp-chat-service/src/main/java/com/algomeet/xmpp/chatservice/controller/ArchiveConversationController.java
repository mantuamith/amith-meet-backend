package com.algomeet.xmpp.chatservice.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.xmpp.chatservice.document.ConversationPreference;
import com.algomeet.xmpp.chatservice.document.ConversationPreferenceId;
import com.algomeet.xmpp.chatservice.dto.ArchiveConversationRequest;
import com.algomeet.xmpp.chatservice.dto.ArchiveConversationResponse;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.ArchiveConversationService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/conversations")
public class ArchiveConversationController {
	private final ArchiveConversationService archiveConversationService;

	/**
	 * 
	 * Create a new archive message entry.
	 */
	@PostMapping("/archive")
	public Mono<ResponseEntity<CommonResponse<ArchiveConversationResponse>>> archiveConversation(
			@Valid @RequestBody ArchiveConversationRequest request) {   

		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
		if (request.getPeerKey() == null && request.getGroupId() == null) {
			 return Mono.error(new ResponseStatusException(
		                HttpStatus.BAD_REQUEST, 
		                "Either peerKey or groupId request parameter must be provided."
		        ));
		}

		UUID conversationId = request.getPeerKey() != null ? request.getPeerKey() : request.getGroupId();

		ConversationPreference document = ConversationPreference.builder()
				.id(new ConversationPreferenceId(conversationId, userKey))				
				.peerKey(request.getPeerKey())
				.groupId(request.getGroupId())
				.archived(true)
				.archivedAt(Instant.now())
				.build();

		return archiveConversationService.archiveConversation(document, request.getSessionId())
				.map(m -> mapToResponse(m))
				.map(responseDto -> ResponseEntity
						.status(HttpStatus.OK)
						.body(CommonResponse.from(ResponseCode.SUCCESS, responseDto)));
	}   

	/**
	 * Remove a archive mapping constraint from a chat window.
	 */
	@DeleteMapping("/unarchive")
	public Mono<ResponseEntity<CommonResponse<Void>>> unpinConversation(
	        @RequestParam(required = false) UUID peerKey,             
	        @RequestParam(required = false) UUID groupId,
	        @RequestParam(value = "sessionId") String sessionId) {

	    if (peerKey == null && groupId == null) {
	        return Mono.error(new ResponseStatusException(
	                HttpStatus.BAD_REQUEST, 
	                "Either peerKey or groupId request parameter must be provided."
	        ));
	    }

	    UUID userKey = UUID.fromString(SecurityUtil.getUserKey());      
	    return archiveConversationService.unarchiveConversation(userKey, peerKey, groupId, sessionId)
	            .map(unpinned -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS)));
	}
		
	/**
	 * Maps the internal domain document into the Long/Epoch-based response DTO format.
	 */
	private ArchiveConversationResponse mapToResponse(ConversationPreference doc) {
		Long createdAtEpoch = doc.getCreatedAt() != null ? doc.getCreatedAt().toEpochMilli() : null;

		return ArchiveConversationResponse.builder()
				.peerKey(doc.getPeerKey())				
				.groupId(doc.getGroupId())
				.createdAt(createdAtEpoch)
				.build();
	}
}
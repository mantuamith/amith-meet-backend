package com.algomeet.xmpp.chatservice.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.document.PinConversation;
import com.algomeet.xmpp.chatservice.document.PinConversationId;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.PinConversationRequest;
import com.algomeet.xmpp.chatservice.dto.PinConversationResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.PinConversationService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/conversations")
public class PinConversationController {
	private final PinConversationService pinConversationService;

	/**
	 * Create a new pinned message entry.
	 */
	@PostMapping("/pin")
	public Mono<ResponseEntity<CommonResponse<PinConversationResponse>>> pinConversation(
			@Valid @RequestBody PinConversationRequest request) {   

		// Calculate the absolute expiration instant if hours are provided
		Instant expirationInstant = null;
		if (request.getExpirationHours() != null && request.getExpirationHours() > 0) {
			expirationInstant = Instant.now().plus(Duration.ofHours(request.getExpirationHours()));
		}

		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
		if (request.getPeerKey() == null && request.getGroupId() == null) {
			throw new RuntimeException("Either peerKey or groupId request field must be provided.");
		}

		UUID conversationId = request.getPeerKey() != null ? request.getPeerKey() : request.getGroupId();

		PinConversation document = PinConversation.builder()
				.id(new PinConversationId(conversationId, userKey))
				.seq(UuidCreator.getTimeOrderedEpoch())
				.peerKey(request.getPeerKey())
				.groupId(request.getGroupId())
				.expiration(expirationInstant)
				.build();

		return pinConversationService.pinConversation(document, request.getSessionId())
				.map(m -> mapToResponse(m))
				.map(responseDto -> ResponseEntity
						.status(HttpStatus.CREATED)
						.body(CommonResponse.from(ResponseCode.SUCCESS, responseDto)));
	}   

	/**
	 * Remove a pin mapping constraint from a chat window.
	 */
	@DeleteMapping("/pin")
	public Mono<ResponseEntity<CommonResponse<Void>>> unpinMessage(
			@RequestParam(required = false) UUID peerKey,             
			@RequestParam(required = false) UUID groupId,
			@RequestParam(value = "sessionId") String sessionId) {

		if (peerKey == null && groupId == null) {
			throw new RuntimeException("Either peerKey or groupId request parameter must be provided.");
		}

		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());      
		return pinConversationService.unpinConversation(userKey, peerKey, groupId, sessionId)
				.flatMap(unpinned -> {
					if (!unpinned) {
						return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ERROR)));
					}
					return Mono.just(ResponseEntity.ok().body(CommonResponse.from(ResponseCode.SUCCESS)));
				});
	}

	/**
	 * Get user pinned conversations
	 * @param peerKey
	 * @return
	 */
	@GetMapping("/pins")
	public Mono<ResponseEntity<CommonResponse<List<PinConversationResponse>>>> findPinnedMessages() {

		UUID userKey = UUID.fromString(SecurityUtil.getUserKey()); 
		return pinConversationService.getPinnedConversations(userKey)
				.map(m -> mapToResponse(m))
				.collectList()
				.map(list -> ResponseEntity
						.ok()
						.body(CommonResponse.from(ResponseCode.SUCCESS, list)));
	}

	/**
	 * Maps the internal domain document into the Long/Epoch-based response DTO format.
	 */
	private PinConversationResponse mapToResponse(PinConversation doc) {
		Long expirationEpoch = doc.getExpiration() != null ? doc.getExpiration().toEpochMilli() : null;
		Long createdAtEpoch = doc.getCreatedAt() != null ? doc.getCreatedAt().toEpochMilli() : null;

		return PinConversationResponse.builder()
				.peerKey(doc.getPeerKey())				
				.groupId(doc.getGroupId())
				.seq(doc.getSeq())
				.expiration(expirationEpoch)
				.createdAt(createdAtEpoch)
				.build();
	}
}
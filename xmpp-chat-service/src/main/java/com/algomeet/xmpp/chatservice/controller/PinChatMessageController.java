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

import com.algomeet.common.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.document.PinChatMessage;
import com.algomeet.xmpp.chatservice.document.PinChatMessageId;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.PinChatMessageRequest;
import com.algomeet.xmpp.chatservice.dto.PinChatMessageResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.PinChatMessageService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class PinChatMessageController {
    private final PinChatMessageService pinChatMessageService;

    /**
     * Create a new pinned message entry.
     */
    @PostMapping("/{peerKey}/pins")
    public Mono<ResponseEntity<CommonResponse<PinChatMessageResponse>>> pinMessage(
    		@PathVariable UUID peerKey,     		
    		@Valid @RequestBody PinChatMessageRequest request) {   
    	
        // Calculate the absolute expiration instant if hours are provided
        Instant expirationInstant = null;
        if (request.getExpirationHours() != null && request.getExpirationHours() > 0) {
            expirationInstant = Instant.now().plus(Duration.ofHours(request.getExpirationHours()));
        }
        
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
        String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);

        PinChatMessage document = PinChatMessage.builder()
                .id(new PinChatMessageId(conversationId, request.getMessageId(), request.getPinnedBy()))
                .seq(UuidCreator.getTimeOrderedEpoch())
                .pinnedForEveryone(request.isPinnedForEveryone())
                .expiration(expirationInstant)
                .build();

        return pinChatMessageService.pinMessage(userKey, request.getSessionId(), peerKey, document)
        		.map(m -> mapToResponse(m, peerKey))
                .map(responseDto -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(CommonResponse.from(ResponseCode.SUCCESS, responseDto)));
    }

    /**
     * Remove a pin mapping constraint from a conversation window.
     */
    @DeleteMapping("/{peerKey}/pins/{messageId}")
    public Mono<ResponseEntity<CommonResponse<Void>>> unpinMessage(
            @PathVariable UUID peerKey,             
            @PathVariable UUID messageId,
            @RequestParam(value = "sessionId") String sessionId) {
    	
    	UUID userKey = UUID.fromString(SecurityUtil.getUserKey());    	
        return pinChatMessageService.unpinMessage(userKey, sessionId, peerKey, messageId)
                .then(Mono.fromCallable(() -> ResponseEntity
                        .ok()
                        .body(CommonResponse.from(ResponseCode.SUCCESS))));
    }
    
    /**
     * Search pinned items dynamically filtered matching safety boundary logic (Self vs Global pinned).
     * Order: Sorted automatically by Sequence ascending.
     * Elements are aggregated via collectList() to safely match the CommonResponse generic envelope structure.
     */
    @GetMapping("/{peerKey}/pins")
    public Mono<ResponseEntity<CommonResponse<List<PinChatMessageResponse>>>> findPinnedMessages(
            @PathVariable UUID peerKey,
            @RequestParam UUID pinnedBy) {
    	
    	UUID userKey = UUID.fromString(SecurityUtil.getUserKey()); 
        return pinChatMessageService.findPinnedMessages(userKey, peerKey, pinnedBy)
                .map(m -> mapToResponse(m, peerKey))
                .collectList()
                .map(list -> ResponseEntity
                        .ok()
                        .body(CommonResponse.from(ResponseCode.SUCCESS, list)));
    }

    /**
     * Maps the internal domain document into the Long/Epoch-based response DTO format.
     */
    private PinChatMessageResponse mapToResponse(PinChatMessage doc, UUID peerKey) {
        Long expirationEpoch = doc.getExpiration() != null ? doc.getExpiration().toEpochMilli() : null;
        Long createdAtEpoch = doc.getCreatedAt() != null ? doc.getCreatedAt().toEpochMilli() : null;

        return PinChatMessageResponse.builder()
                .peerKey(peerKey)
                .messageId(doc.getId().getMessageId())
                .pinnedBy(doc.getId().getPinnedBy())
                .seq(doc.getSeq())
                .pinnedForEveryone(doc.isPinnedForEveryone())
                .expiration(expirationEpoch)
                .createdAt(createdAtEpoch)
                .build();
    }
}
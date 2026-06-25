package com.algomeet.xmpp.chatservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.ChatMessageService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatMessageController {
    private final ChatMessageService chatMessageService;

    /**
     * Resets the counter when a user opens a conversation or clears the conversation history.
     */
    @PostMapping("/{peerKey}/timeline-cutoff")
    public Mono<ResponseEntity<CommonResponse<UnreadCount>>> timelineCutoff(
            @PathVariable("peerKey") UUID peerKey,
            @RequestParam(value = "cutoffMessageId") UUID cutoffMessageId,
            @RequestParam(value = "cutoffStanzaId") UUID cutoffStanzaId) {

        String userKey = SecurityUtil.getUserKey();

        log.info("Executing timeline cutoff and resetting unread count for user: {} against peer: {}", userKey, peerKey);

        return chatMessageService.timelineCutoff(UUID.fromString(userKey), peerKey, cutoffMessageId, cutoffStanzaId)
                .map(unreadCount -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, unreadCount)));
    }
    
    @PostMapping("/{peerKey}/apply-message-retention-policy")
    public Mono<ResponseEntity<CommonResponse<Object>>> applyMessageRetentionPolicy(
    		@PathVariable UUID peerKey,
    		@RequestParam Integer messageRetentionDays) {

    	// Assuming you have a way to extract the current user's UUID (e.g., from a security context or session)
    	// Replace 'currentUserKey' with your actual user context extraction logic.
    	UUID currentUserKey = UUID.fromString(SecurityUtil.getUserKey());  
    	
    	return chatMessageService.applyMessageRetentionPolicy(currentUserKey, peerKey, messageRetentionDays)
    			// .then() waits for completion (empty or not) and switches to your success response
	            .then(Mono.just(ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS))))
	            .onErrorReturn(
	            		IllegalStateException.class, 
	                    ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponse.from(ResponseCode.MESSAGE_RETENTION_UPDATE_IN_PROGRESS))
	            )	 
    			.onErrorResume(Exception.class, ex -> Mono.error(ex));
    }
}
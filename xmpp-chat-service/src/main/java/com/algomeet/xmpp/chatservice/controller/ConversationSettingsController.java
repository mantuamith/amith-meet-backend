package com.algomeet.xmpp.chatservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.ConversationSettingsResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.ConversationSettingsCacheService;
import com.algomeet.xmpp.chatservice.service.ConversationSettingsFacade;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/conversations")
public class ConversationSettingsController {
    private final ConversationSettingsCacheService conversationSettingsCacheService;
    private final ConversationSettingsFacade conversationSettingsFacade;

    /**
     * Search pinned items dynamically filtered matching safety boundary logic (Self vs Global pinned).
     * Order: Sorted automatically by Sequence ascending.
     * Elements are aggregated via collectList() to safely match the CommonResponse generic envelope structure.
     */
    @GetMapping("/{peerKey}/settings")
    public Mono<ResponseEntity<CommonResponse<ConversationSettingsResponse>>> getConversationSetting(
            @PathVariable UUID peerKey) {
    	
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());    	
        return conversationSettingsCacheService.getCachedSettings(userKey, peerKey)
                .map(this::mapToResponse) 
                .map(responseDto -> ResponseEntity
                        .ok()
                        .body(CommonResponse.from(ResponseCode.SUCCESS, responseDto)));
    }
    
    /**
     * Search pinned items dynamically filtered matching safety boundary logic (Self vs Global pinned).
     * Order: Sorted automatically by Sequence ascending.
     * Elements are aggregated via collectList() to safely match the CommonResponse generic envelope structure.
     */
    @GetMapping("/{peerKey}/settings/retention-days")
    public Mono<ResponseEntity<CommonResponse<Integer>>> getMessageRetention(
            @PathVariable UUID peerKey) {
    	
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());     	
        return conversationSettingsCacheService.getCachedSettings(userKey, peerKey)
                .map(this::mapToResponse) 
                .map(responseDto -> ResponseEntity
                        .ok()
                        .body(CommonResponse.from(ResponseCode.SUCCESS, responseDto.getMessageRetentionDays())));
    }
    
    @PostMapping("/{peerKey}/settings/retention-days")
    public Mono<ResponseEntity<CommonResponse<Object>>> updateMessageRetention(
    		@PathVariable UUID peerKey,
    		@RequestParam Integer messageRetentionDays) {

    	// Assuming you have a way to extract the current user's UUID (e.g., from a security context or session)
    	// Replace 'currentUserKey' with your actual user context extraction logic.
    	UUID currentUserKey = UUID.fromString(SecurityUtil.getUserKey());  
    	
    	return conversationSettingsFacade.updateMessageRetention(currentUserKey, peerKey, messageRetentionDays)
    			// .then() waits for completion (empty or not) and switches to your success response
	            .then(Mono.just(ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS))))
	            .onErrorReturn(
	            		IllegalStateException.class, 
	                    ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponse.from(ResponseCode.MESSAGE_RETENTION_UPDATE_IN_PROGRESS))
	            )	 
    			.onErrorResume(Exception.class, ex -> Mono.error(ex));
    }

    /**
     * Maps the internal domain document into the Long/Epoch-based response DTO format.
     */
    private ConversationSettingsResponse mapToResponse(ConversationSettings doc) {
        return ConversationSettingsResponse.builder()
                .messageRetentionDays(doc.getMessageRetentionDays())
                .build();
    }
}
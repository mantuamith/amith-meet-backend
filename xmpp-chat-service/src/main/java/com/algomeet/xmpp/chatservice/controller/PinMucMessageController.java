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

import com.algomeet.xmpp.chatservice.document.PinMucMessage;
import com.algomeet.xmpp.chatservice.document.PinMucMessageId;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.PinMucMessageRequest;
import com.algomeet.xmpp.chatservice.dto.PinMucMessageResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.PinMucMessageService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/muc")
public class PinMucMessageController {
    private final PinMucMessageService pinMucMessageService;

    /**
     * Create a new pinned message entry inside a MUC room.
     */
    @PostMapping("/{groupId}/pins")
    public Mono<ResponseEntity<CommonResponse<PinMucMessageResponse>>> pinMessage(
    		@PathVariable UUID groupId, 
    		@Valid @RequestBody PinMucMessageRequest request) {        
        // Calculate the absolute expiration instant if hours are provided
    	
    	 UUID userKey = UUID.fromString(SecurityUtil.getUserKey());   
        Instant expirationInstant = null;
        if (request.getExpirationHours() != null && request.getExpirationHours() > 0) {
            expirationInstant = Instant.now().plus(Duration.ofHours(request.getExpirationHours()));
        }

        PinMucMessage document = PinMucMessage.builder()
                .id(new PinMucMessageId(groupId, request.getMessageId(), userKey))
                .seq(UuidCreator.getTimeOrderedEpoch())
                .pinnedForEveryone(request.isPinnedForEveryone())
                .expiration(expirationInstant)
                .build();

        return pinMucMessageService.pinMessage(userKey, groupId, request.getSessionId(), document)
                .map(m -> mapToResponse(m))
                .map(responseDto -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(CommonResponse.from(ResponseCode.SUCCESS, responseDto)));
    }

    /**
     * Remove a pin mapping constraint from a MUC room layout window.
     */
    @DeleteMapping("/{groupId}/pins/{messageId}")
    public Mono<ResponseEntity<CommonResponse<Void>>> unpinMessage(
            @PathVariable UUID groupId, 
            @PathVariable UUID messageId,
            @RequestParam(value = "sessionId") String sessionId) {
    	
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());        
        return pinMucMessageService.unpinMessage(userKey, groupId, messageId, sessionId)
                .then(Mono.fromCallable(() -> ResponseEntity
                        .ok()
                        .body(CommonResponse.from(ResponseCode.SUCCESS))));
    }
    
    /**
     * Search pinned items in a MUC space dynamically filtered matching compound indices.
     * Elements are aggregated via collectList() to safely match the CommonResponse generic envelope structure.
     */
    @GetMapping("/{groupId}/pins")
    public Mono<ResponseEntity<CommonResponse<List<PinMucMessageResponse>>>> findPinnedMessages(
            @PathVariable String groupId) {
    	
    	UUID pinnedBy = UUID.fromString(SecurityUtil.getUserKey());    	
        return pinMucMessageService.findPinnedMessages(groupId, pinnedBy)
                .map(m -> mapToResponse(m))
                .collectList()
                .map(list -> ResponseEntity
                        .ok()
                        .body(CommonResponse.from(ResponseCode.SUCCESS, list)));
    }

    /**
     * Maps the internal domain MUC document into the Long/Epoch-based response DTO format.
     */
    private PinMucMessageResponse mapToResponse(PinMucMessage doc) {
        Long expirationEpoch = doc.getExpiration() != null ? doc.getExpiration().toEpochMilli() : null;
        Long createdAtEpoch = doc.getCreatedAt() != null ? doc.getCreatedAt().toEpochMilli() : null;

        return PinMucMessageResponse.builder()
                .groupId(doc.getId().getGroupId())
                .messageId(doc.getId().getMessageId())
                .pinnedBy(doc.getId().getPinnedBy())
                .seq(doc.getSeq())
                .pinnedForEveryone(doc.isPinnedForEveryone())
                .expiration(expirationEpoch)
                .createdAt(createdAtEpoch)
                .build();
    }
}
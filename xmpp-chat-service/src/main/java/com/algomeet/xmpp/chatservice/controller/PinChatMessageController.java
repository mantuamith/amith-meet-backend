package com.algomeet.xmpp.chatservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.document.PinChatMessage;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.PinChatMessageResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.PinChatMessageService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class PinChatMessageController {
    private final PinChatMessageService pinChatMessageService;

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
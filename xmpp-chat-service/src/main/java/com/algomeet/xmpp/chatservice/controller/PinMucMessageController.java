package com.algomeet.xmpp.chatservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.document.PinMucMessage;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.PinMucMessageResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.PinMucMessageService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/muc")
public class PinMucMessageController {
    private final PinMucMessageService pinMucMessageService;

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
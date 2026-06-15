package com.algomeet.xmpp.chatservice.controller;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
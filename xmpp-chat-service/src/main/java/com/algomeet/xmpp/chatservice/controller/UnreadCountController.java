package com.algomeet.xmpp.chatservice.controller;

import com.algomeet.xmpp.chatservice.controller.doc.UnreadCountControllerDoc;
import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.UnreadCountService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chat/unread-counts")
public class UnreadCountController implements UnreadCountControllerDoc{

    @Autowired
    private UnreadCountService unreadCountService;

    /**
     * Gets the full list of unread counts for the authenticated user's inbox.
     */
    @GetMapping
    public Mono<ResponseEntity<CommonResponse<List<UnreadCount>>>> getInboxUnread() {
        String userKey = SecurityUtil.getUserKey();
        return unreadCountService.getUnreadCountsForUser(userKey)
                .collectList()
                .map(data -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, data)));
    }

    /**
     * Gets the total aggregate "badge" count for the authenticated user.
     */
    @GetMapping("/total")
    public Mono<ResponseEntity<CommonResponse<Integer>>> getTotalUnread() {
        String userKey = SecurityUtil.getUserKey();
        return unreadCountService.getTotalUnreadForUser(userKey)
                .map(count -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, count)));
    }

    /**
     * Gets unread count for a specific chat partner.
     */
    @GetMapping("/sender/{senderKey}")
    public Mono<ResponseEntity<CommonResponse<Integer>>> getSpecificUnread(@PathVariable String senderKey) {
        String userKey = SecurityUtil.getUserKey();
        return unreadCountService.getUnreadCount(senderKey, userKey)
                .map(count -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, count)));
    }

    /**
     * Resets the counter when a user opens a conversation or clear the conversation without opening the messagea.
     */
    @Deprecated
    @PostMapping("/sender/{senderKey}/timeline-cutoff")
    public Mono<ResponseEntity<CommonResponse<?>>> timelineCutoff(
    		@PathVariable String senderKey,
    		@RequestParam("cutoffMessageId") UUID cutoffMessageId) {
    	String userKey = SecurityUtil.getUserKey();
    	log.info("Resetting clearing message also unread count for user {} from sender {}", userKey, senderKey);

    	return unreadCountService.resetUnreadCount(UUID.fromString(senderKey), UUID.fromString(userKey), cutoffMessageId)
    			.then(Mono.just(ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS))));
    }
}
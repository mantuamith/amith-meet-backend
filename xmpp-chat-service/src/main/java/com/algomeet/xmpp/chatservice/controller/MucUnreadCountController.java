package com.algomeet.xmpp.chatservice.controller;

import com.algomeet.xmpp.chatservice.controller.doc.MucUnreadCountControllerDoc;
import com.algomeet.xmpp.chatservice.document.MucUnreadCount;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.MucUnreadCountService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat/muc-unread-counts")
@RequiredArgsConstructor
public class MucUnreadCountController implements MucUnreadCountControllerDoc{

    private final MucUnreadCountService mucUnreadCountService;

    /**
     * Gets a list of all rooms with unread messages for the authenticated user.
     */
    @GetMapping
    public Mono<CommonResponse<List<MucUnreadCount>>> getUnreadRooms() {
        String userKey = SecurityUtil.getUserKey();
        return mucUnreadCountService.getUnreadCountsByUser(userKey)
                .collectList()
                .map(data -> CommonResponse.from(ResponseCode.SUCCESS, data));
    }

    /**
     * Gets the total aggregate unread count for all MUCs for a user.
     */
    @GetMapping("/total")
    public Mono<CommonResponse<Integer>> getTotalUnread() {
        String userKey = SecurityUtil.getUserKey();
        return mucUnreadCountService.getTotalUnreadCount(userKey)
                .map(count -> CommonResponse.from(ResponseCode.SUCCESS, count));
    }

    /**
     * Gets the unread count for a specific room.
     */
    @GetMapping("/room/{roomId}")
    public Mono<CommonResponse<Integer>> getRoomUnread(@PathVariable String roomId) {
        String userKey = SecurityUtil.getUserKey();
        return mucUnreadCountService.getUnreadCount(userKey, roomId)
                .map(count -> CommonResponse.from(ResponseCode.SUCCESS, count));
    }

    /**
     * Resets the unread counter for a specific room to zero.
     */
    @PostMapping("/room/{roomId}/reset")
    public Mono<CommonResponse<Void>> resetRoomCount(@PathVariable String roomId) {
        String userKey = SecurityUtil.getUserKey();
        return mucUnreadCountService.resetUnreadCount(userKey, roomId)
                .then(Mono.fromCallable(() -> CommonResponse.from(ResponseCode.SUCCESS)));
    }
}
package com.algomeet.xmpp.chatservice.controller;

import com.algomeet.xmpp.chatservice.controller.doc.MucUnreadCountControllerDoc;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.MucUnreadCount;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.MucUnreadCountService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<CommonResponse<List<MucUnreadCount>>> getUnreadRooms() {
        String userKey = SecurityUtil.getUserKey();
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
        		mucUnreadCountService.getUnreadCountsByUser(userKey)));
    }

    /**
     * Aggregates and returns the global unread message badge count across all rooms for the authenticated user.
     * 
     * @return A {@link CommonResponse} wrapping the total summation integer.
     */
    @GetMapping("/total")
    public ResponseEntity<CommonResponse<Integer>> getTotalUnread() {
        // Step 1: Securely extract the unique identifier of the requesting user
        String userKey = SecurityUtil.getUserKey();
        
        // Step 2: Fetch the active room unread metrics block from the service layer
        List<MucUnreadCount> unreadCounts = mucUnreadCountService.getUnreadCountsByUser(userKey);
        
        // Step 3: Stream and sum up the counts using an inline integer reduction accumulator
        int totalUnreadBadge = unreadCounts.stream()
                .mapToInt(MucUnreadCount::getUnreadCount)
                .sum();
        
        // Step 4: Map directly to your corporate envelope schema payload
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, totalUnreadBadge));
    }

    /**
     * Gets the unread count for a specific room.
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<CommonResponse<Integer>> getRoomUnread(@PathVariable String roomId) {
        String userKey = SecurityUtil.getUserKey();
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
        		mucUnreadCountService.getUnreadCount(userKey, roomId)));
    }
}
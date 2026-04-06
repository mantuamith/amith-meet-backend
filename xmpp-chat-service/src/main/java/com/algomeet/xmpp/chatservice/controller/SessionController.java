package com.algomeet.xmpp.chatservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.SessionService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService; // Replace with your actual service name

    /**
     * Manually removes a specific session.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<CommonResponse<?>> removeSession(
            @PathVariable String sessionId) {
        String userKey = SecurityUtil.getUserKey();
        log.info("Request to manually evict session {} for user {}", sessionId, userKey);
        
        try {
            sessionService.removeSession(userKey, sessionId);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (Exception e) {
            log.error("Failed to remove session {}", sessionId, e);
            throw e;
        }
    }
}
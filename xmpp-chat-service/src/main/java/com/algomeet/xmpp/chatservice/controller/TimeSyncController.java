package com.algomeet.xmpp.chatservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.controller.doc.TimeSyncControllerDoc;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;

import java.time.Instant;

@RestController
@RequestMapping("/api/chat/time")
public class TimeSyncController implements TimeSyncControllerDoc {

    @GetMapping("/sync")
    public ResponseEntity<CommonResponse<Long>> getServerTime() {
        // Return the exact millisecond timestamp from the server's system clock
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, (Instant.now().toEpochMilli())));
    }    
}
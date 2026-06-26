package com.algomeet.xmpp.chatservice.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.BatchMediaShareRequest;

import jakarta.validation.Valid;

@FeignClient(name = "media-service", url = "${feign.client.media-service.url}")
public interface MediaClient {
	
	@PostMapping("/internal/media/{mediaId}/share")
	ResponseEntity<?> share(@PathVariable UUID mediaId,
            @RequestParam String userKey,
            @RequestParam(required = false) List<String> shareWithUserKeys,
            @RequestParam(required = false) UUID groupId,
            @RequestParam UUID messageId);	
	
	@PostMapping("/internal/media/share")
    ResponseEntity<?> batchShare(
    		@RequestParam String userKey,
            @RequestBody @Valid BatchMediaShareRequest request
    );
}


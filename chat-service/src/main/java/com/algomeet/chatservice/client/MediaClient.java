package com.algomeet.chatservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "media-service", url = "${media.service.url}")
public interface MediaClient {
	
	@PostMapping("/internal/media/{mediaId}/share")
	public ResponseEntity<?> share(@PathVariable String mediaId, 
			@RequestParam String userKey, @RequestParam List<String> shareWithUserKeys);
	
	@DeleteMapping("/internal/media/{mediaId}")
	public ResponseEntity<?> delete(@PathVariable String mediaId,
			@RequestParam String userKey,
			@RequestParam(required = false) List<String> deleteWithUserKeys);
}


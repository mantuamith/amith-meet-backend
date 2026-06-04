package com.algomeet.chatservice.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "media-service", url = "${media.service.url}")
public interface MediaClient {
	
	@PostMapping("/internal/media/{mediaId}/share")
	public void share(@PathVariable String mediaId, 
			@RequestParam String userKey, @RequestParam List<String> shareWithUserKeys,
			@RequestParam UUID messageId);
	
	@DeleteMapping("/internal/media/{mediaId}/access")
	public void delete(@PathVariable String mediaId,
			@RequestParam String userKey,
			@RequestParam(required = false) List<String> deleteWithUserKeys,
			@RequestParam UUID messageId);
}


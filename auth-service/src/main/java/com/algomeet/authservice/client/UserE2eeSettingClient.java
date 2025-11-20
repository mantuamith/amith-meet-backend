package com.algomeet.authservice.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.UserE2eeSettingRequest;
import com.algomeet.authservice.dto.UserE2eeSettingResponse;

@FeignClient(name = "user-e2ee-settings-user-service", url = "${feign.client.user-service.url}")
public interface UserE2eeSettingClient {

	@GetMapping("/internal/user-e2ee-settings/{userKey}")
	UserE2eeSettingResponse getById(@PathVariable("userKey") UUID userKey);

	@PostMapping("/internal/user-e2ee-settings/{userKey}")
	UserE2eeSettingResponse createOrUpdate(
			@PathVariable("userKey") UUID userKey,
			@RequestBody UserE2eeSettingRequest request);

	@DeleteMapping("/internal/user-e2ee-settings/{userKey}")
	void delete(@PathVariable("userKey") UUID userKey);
}

package com.algomeet.authservice.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.E2eeUserSettingRequest;
import com.algomeet.authservice.dto.E2eeUserSettingResponse;

@FeignClient(name = "e2ee-user-settings-user-service", url = "${feign.client.user-service.url}")
public interface E2eeUserSettingClient {

	@GetMapping("/internal/e2ee-user-settings/{userKey}")
	E2eeUserSettingResponse getById(@PathVariable("userKey") UUID userKey);

	@PostMapping("/internal/e2ee-user-settings/{userKey}")
	E2eeUserSettingResponse createOrUpdate(
			@PathVariable("userKey") UUID userKey,
			@RequestBody E2eeUserSettingRequest request);

	@DeleteMapping("/internal/e2ee-user-settings/{userKey}")
	void delete(@PathVariable("userKey") UUID userKey);
}

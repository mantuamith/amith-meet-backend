package com.algomeet.signalservice.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.signalservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.signalservice.dto.StorageUsageResponse;


@FeignClient(name = "media-service", url = "${feign.client.media-service.url}")
public interface MediaClient {

	@PutMapping("/internal/media/users/{userKey}/storage-usage")
	public StorageUsageResponse adjustStorageUsage(
			@PathVariable UUID userKey,
			@RequestBody StorageUsageAdjustmentRequest request);

	@DeleteMapping("/internal/media/users/{userKey}/storage-usage")
	public void deleteUserStorageUsage(@PathVariable("userKey") UUID userKey);
}


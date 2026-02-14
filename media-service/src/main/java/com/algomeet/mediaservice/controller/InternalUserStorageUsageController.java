package com.algomeet.mediaservice.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.dto.StorageUsageResponse;
import com.algomeet.mediaservice.service.impl.UserStorageUsageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/media/users")
@RequiredArgsConstructor
public class InternalUserStorageUsageController {				

    private final UserStorageUsageService service;
    
    @GetMapping("/{userKey}/storage-usage")
	public StorageUsageResponse getUserStorageUsage(@PathVariable("userKey") UUID userKey) {
    	return service.getUsage(userKey);
	}	
    
    /**
     * Adjusts user storage counters (increase/decrease).
     */
    @PutMapping("/{userKey}/storage-usage")
    public StorageUsageResponse adjustStorageUsage(
            @PathVariable UUID userKey,
            @RequestBody StorageUsageAdjustmentRequest request) {

        return service.adjustUsage(userKey, request);
    }
    
    @DeleteMapping("/{userKey}/storage-usage")
	public void deleteUserStorageUsage(@PathVariable("userKey") UUID userKey) {
		service.deleteUsage(userKey);
	}	
}

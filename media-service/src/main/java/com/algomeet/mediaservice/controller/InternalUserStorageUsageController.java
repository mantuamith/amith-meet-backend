package com.algomeet.mediaservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.dto.StorageUsageResponse;
import com.algomeet.mediaservice.enums.ResponseCode;
import com.algomeet.mediaservice.service.impl.UserStorageUsageService;

import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/media/users")
@RequiredArgsConstructor
public class InternalUserStorageUsageController {				

    private final UserStorageUsageService service;
    
    @GetMapping("/{userKey}/storage-usage")
	public ResponseEntity<CommonResponse<StorageUsageResponse>> getUserStorage(@PathVariable("userKey") UUID userKey) {
		StorageUsageResponse response = service.getUsage(userKey);

        return ResponseEntity.ok(
                CommonResponse.from(ResponseCode.SUCCESS, response)
        );
	}	
    
    /**
     * Adjusts user storage counters (increase/decrease).
     */
    @PatchMapping("/{userKey}/storage-usage")
    public ResponseEntity<CommonResponse<StorageUsageResponse>> adjustStorage(
            @PathVariable UUID userKey,
            @RequestBody StorageUsageAdjustmentRequest request) {

        StorageUsageResponse response = service.adjustUsage(userKey, request);

        return ResponseEntity.ok(
                CommonResponse.from(ResponseCode.SUCCESS, response)
        );
    }
}

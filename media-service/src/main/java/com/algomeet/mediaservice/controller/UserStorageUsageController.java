package com.algomeet.mediaservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.StorageUsageResponse;
import com.algomeet.mediaservice.enums.ResponseCode;
import com.algomeet.mediaservice.service.impl.UserStorageUsageService;
import com.algomeet.mediaservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/media/users")
@RequiredArgsConstructor
public class UserStorageUsageController {		

    private final UserStorageUsageService service;
	
	@GetMapping("/me/storage-usage")
	public ResponseEntity<CommonResponse<StorageUsageResponse>> getUserStorage() {
		StorageUsageResponse response = service.getUsage(UUID.fromString(SecurityUtil.getUserKey()));

        return ResponseEntity.ok(
                CommonResponse.from(ResponseCode.SUCCESS, response)
        );
	}
		
	@PreAuthorize("hasAnyRole('SA','ADMIN')")
	@GetMapping("/{userKey}/storage-usage")
	public ResponseEntity<CommonResponse<StorageUsageResponse>> getUserStorage(@PathVariable UUID userKey) {
		StorageUsageResponse response = service.getUsage(userKey);

        return ResponseEntity.ok(
                CommonResponse.from(ResponseCode.SUCCESS, response)
        );
	}	
}

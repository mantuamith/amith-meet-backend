package com.algomeet.signalservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.dto.OneTimePreKeysRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.OneTimePreKeyIsNotAvailableException;
import com.algomeet.signalservice.service.OneTimePreKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal/v2/prekeys/one-time")
@RequiredArgsConstructor
public class OneTimePreKeyController {
	private final OneTimePreKeyService service;

	@PostMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<OneTimePreKeyResponse>> create(@PathVariable Integer deviceId, @RequestBody OneTimePreKeysRequest request) {
		service.create(UUID.fromString(SecurityUtil.getUserKey()), deviceId, request);
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS));
	}

	@GetMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<OneTimePreKeyResponse>> getAvailable(@PathVariable UUID userKey,
			@PathVariable Integer deviceId) {	
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS, service.getAvailable(userKey, deviceId)));			
		} catch(OneTimePreKeyIsNotAvailableException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.ONE_TIME_PRE_KEY_NOT_AVAILABLE));
		}
	}	
	
	@GetMapping("/{deviceId}/count")
	public ResponseEntity<CommonResponse<Long>> getAvailablePrekeysCount(@PathVariable UUID userKey,
			@PathVariable Integer deviceId) {	
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS, service.getAvailablePrekeysCount(userKey, deviceId)));		
	}	
	
	@DeleteMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<?>> deleteAll(@PathVariable Integer deviceId) {
		service.delete(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS));
	}
}

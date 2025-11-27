package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.OneTimePreKeyControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.dto.OneTimePreKeysRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.OneTimePreKeyIsNotAvailableException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.OneTimePreKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal/v2/devices")
@RequiredArgsConstructor
public class OneTimePreKeyController implements OneTimePreKeyControllerDoc{
	private final OneTimePreKeyService service;

	@PostMapping("/{deviceId}/prekeys/one-time")
	public ResponseEntity<CommonResponse<List<OneTimePreKeyResponse>>> create(@PathVariable Integer deviceId, @Validated @RequestBody OneTimePreKeysRequest request) {
		try {
			List<OneTimePreKeyResponse> savedList = service.create(UUID.fromString(SecurityUtil.getUserKey()), deviceId, request);
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS, savedList));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}

	@GetMapping("/{deviceId}/prekeys/one-time")
	public ResponseEntity<CommonResponse<OneTimePreKeyResponse>> getAvailable(@RequestParam UUID userKey,
			@PathVariable Integer deviceId) {	
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS, service.getAvailable(userKey, deviceId)));			
		} catch(OneTimePreKeyIsNotAvailableException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.ONE_TIME_PRE_KEY_NOT_AVAILABLE));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}	

	@GetMapping("/{deviceId}/prekeys/one-time/count")
	public ResponseEntity<CommonResponse<Long>> getAvailablePrekeysCount(@PathVariable Integer deviceId) {	
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS, service.getAvailablePrekeysCount(UUID.fromString(SecurityUtil.getUserKey()), deviceId)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}	

	@DeleteMapping("/{deviceId}/prekeys/one-time")
	public ResponseEntity<CommonResponse<?>> deleteAll(@PathVariable Integer deviceId) {
		try {
			service.delete(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.ONE_TIME_PRE_KEY_NOT_FOUND));
		}
	}
}

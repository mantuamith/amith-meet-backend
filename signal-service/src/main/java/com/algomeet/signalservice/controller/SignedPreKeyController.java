package com.algomeet.signalservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.SignedPreKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/signal/v2/signed-prekeys")
public class SignedPreKeyController {

	private final SignedPreKeyService service;

	public SignedPreKeyController(SignedPreKeyService service) {
		this.service = service;
	}

	@GetMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<SignedPreKeyResponse>> get(
			@PathVariable Integer deviceId, 
			@RequestParam Optional<UUID> userKey
			) {		
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS,
							service.getById(userKey.orElse(UUID.fromString(SecurityUtil.getUserKey())), deviceId)));

		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SIGNED_PRE_KEY_NOT_FOUND));
		}
	}

	@PutMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<SignedPreKeyResponse>> update(
			@PathVariable Integer deviceId,
			@RequestBody SignedPreKeyRequest request
			) {		
		try {
			SignedPreKeyResponse response = service.update(UUID.fromString(SecurityUtil.getUserKey()), deviceId, request);
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS,
							response));

		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SIGNED_PRE_KEY_NOT_FOUND));
		}	
	}

	@DeleteMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<?>> delete(
			@PathVariable Integer deviceId
			) {
		try {
			service.delete(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS));

		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SIGNED_PRE_KEY_NOT_FOUND));
		}
	}
}
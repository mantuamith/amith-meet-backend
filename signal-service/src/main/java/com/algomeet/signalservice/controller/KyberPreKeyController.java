package com.algomeet.signalservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.algomeet.signalservice.controller.swagger.KyberPreKeyControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.KyberPreKeyResponse;
import com.algomeet.signalservice.entity.KyberPreKeyId;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.KyberPreKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/signal/v2/devices")
@RequiredArgsConstructor
public class KyberPreKeyController implements KyberPreKeyControllerDoc{
	private final KyberPreKeyService service;

	@GetMapping("/{deviceId}/kyber-prekeys")
	public ResponseEntity<CommonResponse<KyberPreKeyResponse>> retrieve(
			@PathVariable Integer deviceId, Optional<UUID> userKey) {
		KyberPreKeyId id = new KyberPreKeyId(userKey.orElse(UUID.fromString(SecurityUtil.getUserKey())), deviceId);		
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS, service.getPreKey(id)));

		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.KYBER_PRE_KEY_NOT_FOUND));
		}
	}

	@PutMapping("/{deviceId}/kyber-prekeys")
	public ResponseEntity<CommonResponse<KyberPreKeyResponse>> update(
			@PathVariable Integer deviceId,
			@RequestBody KyberPreKeyRequest request) {

		KyberPreKeyId id = new KyberPreKeyId(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS, service.updatePreKey(id, request)));

		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.KYBER_PRE_KEY_NOT_FOUND));
		}
	}
}
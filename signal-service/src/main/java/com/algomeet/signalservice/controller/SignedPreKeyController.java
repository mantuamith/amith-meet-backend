package com.algomeet.signalservice.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.SignedPreKeyControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.SignedPreKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

@RestController
@RequestMapping("/signal/v2/devices")
public class SignedPreKeyController implements SignedPreKeyControllerDoc{

	private final SignedPreKeyService service;

	public SignedPreKeyController(SignedPreKeyService service) {
		this.service = service;
	}

	@GetMapping("/{deviceId}/signed-prekeys")
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

	@PutMapping("/{deviceId}/signed-prekeys")
	public ResponseEntity<CommonResponse<SignedPreKeyResponse>> update(
			@PathVariable Integer deviceId,
			@Validated @RequestBody SignedPreKeyRequest request
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
}
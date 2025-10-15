package com.algomeet.signalingservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.IdentityOneTimeKeyRequest;
import com.algomeet.signalingservice.dto.IdentityOneTimeKeyResponse;
import com.algomeet.signalingservice.dto.UserIdentityKeyRequest;
import com.algomeet.signalingservice.dto.UserIdentityKeyResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.IdentityKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.exceptions.UserDontHaveOneTimeKeyAvailableException;
import com.algomeet.signalingservice.exceptions.UserKeyAlreadyExistsException;
import com.algomeet.signalingservice.service.UserIdentityKeyService;
import com.algomeet.signalingservice.util.SecurityUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signaling/keys")
@RequiredArgsConstructor
public class UserIdentityKeyController {
	private final UserIdentityKeyService keyService;

	// Register a new user identity key
	@PostMapping("/identity")
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> registerUserIdentity(@Valid @RequestBody UserIdentityKeyRequest request) {
		try {
			UserIdentityKeyResponse savedUserIdentityKey = keyService.registerUserIdentity(UUID.fromString(SecurityUtil.getUserKey()), request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_REGISTER_SUCCESS, savedUserIdentityKey));

		} catch(UserKeyAlreadyExistsException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.from(ResponseCode.USER_KEY_ALREADY_EXISTS));
		} catch(IdentityKeyAlreadyExistsException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_ALREADY_EXISTS));
		}		
	}
	
	// Update user identity key
	@PutMapping("/identity")
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> updateUserIdentity(@Valid @RequestBody UserIdentityKeyRequest request) {
		try {
			UserIdentityKeyResponse savedUserIdentityKey = keyService.updateUserIdentity(UUID.fromString(SecurityUtil.getUserKey()), request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_REGISTER_SUCCESS, savedUserIdentityKey));

		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		}	
	}
	
	// Get identity key 
	@GetMapping("/identity")
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> getUserOneTimeKey(@RequestParam UUID userKey) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityKey(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		}
	}
	
	// Get identity key and one-time key of a user
	@GetMapping("/identity-and-one-time")
	public ResponseEntity<CommonResponse<IdentityOneTimeKeyResponse>> getUserIdentityAndOneTimeKey(@RequestParam UUID userKey) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityAndOneTimeKey(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		} catch(UserDontHaveOneTimeKeyAvailableException ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_NOT_AVAILABLE));
		}

	}

	// Add one-time key for existing user identity key
	@PostMapping("/one-time")
	public ResponseEntity<CommonResponse<List<IdentityOneTimeKeyResponse>>> addOneTimeKeys(@Valid @RequestBody IdentityOneTimeKeyRequest request) {
		try {
			List<IdentityOneTimeKeyResponse> savedKeys = keyService.addOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()), request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ADD_SUCCESS, savedKeys));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		}		
	}

	// Get one-time keys for existing user identity key
	@GetMapping("/one-time")
	public ResponseEntity<CommonResponse<List<IdentityOneTimeKeyResponse>>> getOneTimeKeys() {
		try {
			List<IdentityOneTimeKeyResponse> savedKeys = keyService.getOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedKeys));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}

	// Delete one-time key for existing user
	@DeleteMapping("/one-time-key/{id}")
	public ResponseEntity<CommonResponse<?>> deleteOneTimeKey(@PathVariable Long id) {
		try {
			keyService.deleteOneTimeKey(id);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ID_NOT_FOUND));
		}
	}	
}
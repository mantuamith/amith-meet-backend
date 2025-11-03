package com.algomeet.signalingservice.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserIdentityAndOneTimeKeysResponse;
import com.algomeet.signalingservice.dto.UserIdentityKeyRequest;
import com.algomeet.signalingservice.dto.UserIdentityKeyResponse;
import com.algomeet.signalingservice.dto.UserOneTimeKeyRequest;
import com.algomeet.signalingservice.dto.UserOneTimeKeyResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.IdentityKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.NoUserOneTimeKeyIsAvailableException;
import com.algomeet.signalingservice.exceptions.OneTimeKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.OneTimeKeysReservedMaxLimitExceededException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.UserKeyService;
import com.algomeet.signalingservice.util.SecurityUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller responsible for managing user identity keys and one-time keys
 * in the signaling service. Provides endpoints for registering, updating,
 * retrieving, and deleting identity and one-time keys.
 */

@Slf4j
@RestController
@RequestMapping("/signaling/keys")
@RequiredArgsConstructor
public class UserKeyController {
	private final UserKeyService keyService;

	@PostMapping("/identity")
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> registerUserIdentity(@Valid @RequestBody UserIdentityKeyRequest request) {
		try {
			UserIdentityKeyResponse savedUserIdentityKey = keyService.registerUserIdentity(UUID.fromString(SecurityUtil.getUserKey()), request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_REGISTER_SUCCESS, savedUserIdentityKey));

		} catch(IdentityKeyAlreadyExistsException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_ALREADY_EXISTS));
		}		
	}
		
	@GetMapping("/identity")
	public ResponseEntity<CommonResponse<List<UserIdentityKeyResponse>>> getUserIdentityKeys(@RequestParam("userKey") Optional<UUID> userKeyOpt) {
		try {
			UUID userKey = userKeyOpt.orElse(UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityKeys(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		}
	}
	
	@DeleteMapping("/identity/{identityKey}")
	public ResponseEntity<CommonResponse<?>> deleteIndentity(@PathVariable String identityKey) {
		try {
			keyService.deleteIdentityKey(UUID.fromString(SecurityUtil.getUserKey()), identityKey);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}
	
	@GetMapping("/identity-and-one-time")
	public ResponseEntity<CommonResponse<UserIdentityAndOneTimeKeysResponse>> getUserIdentityAndOneTimeKeys(@RequestParam UUID userKey) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityAndOneTimeKeys(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		} catch(NoUserOneTimeKeyIsAvailableException ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_IS_NOT_AVAILABLE));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@PostMapping("/identity/{identityKey}/one-time")
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> addOneTimeKeys(@PathVariable String identityKey, 
			@Valid @RequestBody UserOneTimeKeyRequest request) {
		try {
			List<UserOneTimeKeyResponse> savedKeys = keyService.addOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()), identityKey, request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ADD_SUCCESS, savedKeys));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		} catch (OneTimeKeyAlreadyExistsException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
					new CommonResponse(ResponseCode.ONE_TIME_KEY_ALREADY_EXISTS.getCode(), 
							ResponseCode.ONE_TIME_KEY_ALREADY_EXISTS.getMessage() + " " + ex.getMessage()));
		} catch (OneTimeKeysReservedMaxLimitExceededException ex) {
			log.error("Error {}", ex.getMessage(), ex);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
					new CommonResponse(ResponseCode.ONE_TIME_KEY_RESERVED_MAX_LIMIT_EXCEEDED.getCode(), 
							ResponseCode.ONE_TIME_KEY_RESERVED_MAX_LIMIT_EXCEEDED.getMessage() + " " + ex.getMessage()));
		}
	}

	@GetMapping("/identity/{identityKey}/one-time")
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> getOneTimeKeys(@PathVariable String identityKey) {
		try {
			List<UserOneTimeKeyResponse> savedKeys = keyService.getOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()), identityKey);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedKeys));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}	

	@DeleteMapping("/identity/one-time/{id}")
	public ResponseEntity<CommonResponse<?>> deleteOneTimeKey(@PathVariable Long id) {
		try {
			keyService.deleteOneTimeKey(id, UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ID_NOT_FOUND));
		}
	}	
	
	@DeleteMapping("/identity/one-time")
	public ResponseEntity<CommonResponse<?>> deleteOneTimeKeys(@RequestParam List<Long> ids) {
		try {
			for (Long id: ids) {
				keyService.deleteOneTimeKey(id, UUID.fromString(SecurityUtil.getUserKey()));
			}
			
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ID_NOT_FOUND));
		}
	}
}
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserIdentityAndOneTimeKeyResponse;
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
import com.algomeet.signalingservice.exceptions.UserKeyAlreadyExistsException;
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

	/**
	 * Registers a new user identity key for the authenticated user.
	 *
	 * @param request the {@link UserIdentityKeyRequest} containing the identity key details
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} with the created identity key information
	 * @throws UserKeyAlreadyExistsException if the user key already exists
	 * @throws IdentityKeyAlreadyExistsException if the identity key is already registered
	 */
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
	
	/**
	 * Updates the existing user identity key for the authenticated user.
	 *
	 * @param request the {@link UserIdentityKeyRequest} containing updated identity key details
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} with the updated identity key information
	 * @throws RecordNotFoundException if the user identity key record is not found
	 */
	@PutMapping("/identity")
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> updateUserIdentity(@Valid @RequestBody UserIdentityKeyRequest request) {
		try {
			UserIdentityKeyResponse savedUserIdentityKey = keyService.updateUserIdentity(UUID.fromString(SecurityUtil.getUserKey()), request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_UPDATE_SUCCESS, savedUserIdentityKey));

		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		}	
	}
	
	/**
	 * Retrieves the identity key of the specified user or the currently authenticated user if no parameter is provided.
	 *
	 * @param userKeyOpt optional UUID of the user whose identity key is being requested
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} with the user's identity key information
	 * @throws RecordNotFoundException if the identity key record is not found
	 */
	@GetMapping("/identity")
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> getUserIdentityKey(@RequestParam("userKey") Optional<UUID> userKeyOpt) {
		try {
			UUID userKey = userKeyOpt.orElse(UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityKey(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		}
	}
	
	/**
	 * Deletes the identity key of the currently authenticated user.
	 *
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} confirming the deletion status
	 * @throws RecordNotFoundException if no identity key is found for the current user
	 */
	@DeleteMapping("/identity")
	public ResponseEntity<CommonResponse<?>> deleteIndentity() {
		try {
			keyService.deleteIdentityKey(UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}
	
	/**
	 * Retrieves both the identity key and one-time key of a specified user.
	 *
	 * @param userKey the UUID of the user whose keys are being requested
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} with identity and one-time key information
	 * @throws RecordNotFoundException if the user record is not found
	 * @throws NoUserOneTimeKeyIsAvailableException if no one-time key is available for the user
	 */
	@GetMapping("/identity-and-one-time")
	public ResponseEntity<CommonResponse<UserIdentityAndOneTimeKeyResponse>> getUserIdentityAndOneTimeKey(@RequestParam UUID userKey) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityAndOneTimeKey(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_KEY_NOT_FOUND));
		} catch(NoUserOneTimeKeyIsAvailableException ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_IS_NOT_AVAILABLE));
		}

	}

	/**
	 * Adds new one-time keys for the authenticated user's identity key.
	 *
	 * @param request the {@link UserOneTimeKeyRequest} containing one-time key data
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} with the list of added one-time keys
	 * @throws RecordNotFoundException if the user identity key is not found
	 * @throws OneTimeKeyAlreadyExistsException if any provided one-time key already exists
	 * @throws OneTimeKeysReservedMaxLimitExceededException if the user exceeds the maximum reserved one-time keys limit
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@PostMapping("/one-time")
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> addOneTimeKeys(@Valid @RequestBody UserOneTimeKeyRequest request) {
		try {
			List<UserOneTimeKeyResponse> savedKeys = keyService.addOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()), request);
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

	/**
	 * Retrieves all one-time keys associated with the authenticated user's identity key.
	 *
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} with the list of one-time keys
	 * @throws RecordNotFoundException if the identity key is not found for the user
	 */
	@GetMapping("/one-time")
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> getOneTimeKeys() {
		try {
			List<UserOneTimeKeyResponse> savedKeys = keyService.getOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedKeys));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}	

	/**
	 * Deletes a specific one-time key for the authenticated user.
	 *
	 * @param id the ID of the one-time key to be deleted
	 * @return a {@link ResponseEntity} containing {@link CommonResponse} confirming the deletion result
	 * @throws RecordNotFoundException if the one-time key record is not found
	 */
	@DeleteMapping("/one-time/{id}")
	public ResponseEntity<CommonResponse<?>> deleteOneTimeKey(@PathVariable Long id) {
		try {
			keyService.deleteOneTimeKey(id, UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ID_NOT_FOUND));
		}
	}	
}
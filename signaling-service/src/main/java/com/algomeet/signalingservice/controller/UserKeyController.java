package com.algomeet.signalingservice.controller;

import java.util.ArrayList;
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

import com.algomeet.signalingservice.controller.swagger.UserKeyControllerDoc;
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
 *
 * <p>This controller delegates business logic to {@link UserKeyService} and
 * returns standardized {@link CommonResponse} payloads for API clients.
 * </p>
 *
 * <p>All methods require user authentication. The {@link SecurityUtil#getUserKey()} utility
 * is used to extract the current user's UUID from the security context.</p>
 *
 * @author 
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/signaling/keys")
@RequiredArgsConstructor
public class UserKeyController implements UserKeyControllerDoc{
	private final UserKeyService keyService;

	/**
	 * Registers a new user identity key for the authenticated user.
	 *
	 * @param request the request payload containing the identity key information
	 * @return a {@link CommonResponse} containing the saved {@link UserIdentityKeyResponse}
	 *         if successful, or an error response if the key already exists
	 */
	@Override
	@PostMapping("/identity")
	public ResponseEntity<CommonResponse<UserIdentityKeyResponse>> registerUserIdentity(@Valid @RequestBody UserIdentityKeyRequest request) {
		try {
			UserIdentityKeyResponse savedUserIdentityKey = keyService.registerUserIdentity(UUID.fromString(SecurityUtil.getUserKey()), request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_REGISTER_SUCCESS, savedUserIdentityKey));

		} catch(IdentityKeyAlreadyExistsException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_ALREADY_EXISTS));
		}		
	}
	
	/**
	 * Retrieves all identity keys associated with a user.
	 *
	 * @param userKeyOpt optional query parameter for specifying a user key.
	 *                   If not provided, the key of the authenticated user is used.
	 * @return a {@link CommonResponse} containing a list of {@link UserIdentityKeyResponse} objects
	 */
	@Override
	@GetMapping("/identity")
	public ResponseEntity<CommonResponse<List<UserIdentityKeyResponse>>> getUserIdentityKeys(@RequestParam("userKey") Optional<UUID> userKeyOpt) {
		try {
			UUID userKey = userKeyOpt.orElse(UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityKeys(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}
	
	/**
	 * Deletes a specific user identity key belonging to the authenticated user.
	 *
	 * @param identityKey the identity key string to delete
	 * @return a {@link CommonResponse} indicating the result of the deletion operation
	 */
	@Override
	@DeleteMapping("/identity/{identityKey}")
	public ResponseEntity<CommonResponse<?>> deleteIndentity(@PathVariable String identityKey) {
		try {
			keyService.deleteIdentityKey(UUID.fromString(SecurityUtil.getUserKey()), identityKey);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.IDENTITY_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}
	
	/**
	 * Retrieves a user’s identity key and available one-time keys.
	 *
	 * @param userKey the UUID of the user whose keys to fetch
	 * @return a {@link CommonResponse} containing {@link UserIdentityAndOneTimeKeysResponse}
	 */
	@Deprecated
	@Override
	@GetMapping("/identity-and-one-time")
	public ResponseEntity<CommonResponse<UserIdentityAndOneTimeKeysResponse>> getUserIdentityAndOneTimeKeys(@RequestParam UUID userKey) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, keyService.getUserIdentityAndOneTimeKeys(userKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		} catch(NoUserOneTimeKeyIsAvailableException ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_IS_NOT_AVAILABLE));
		}
	}

	/**
	 * Adds new one-time keys for a given identity key.
	 *
	 * @param identityKey the identity key associated with the one-time keys
	 * @param request     the request payload containing one-time key data
	 * @return a {@link CommonResponse} containing a list of created {@link UserOneTimeKeyResponse}
	 */
	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@PostMapping("/identity/{identityKey}/one-time")
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> addOneTimeKeys(@PathVariable String identityKey, 
			@Valid @RequestBody UserOneTimeKeyRequest request) {
		try {
			List<UserOneTimeKeyResponse> savedKeys = keyService.addOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()), identityKey, request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ADD_SUCCESS, savedKeys));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
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
	 * Retrieves all one-time keys for a specific identity key.
	 *
	 * @param identityKey the identity key whose one-time keys are requested
	 * @return a {@link CommonResponse} containing a list of {@link UserOneTimeKeyResponse}
	 */
	@Override
	@GetMapping("/identity/{identityKey}/one-time")
	public ResponseEntity<CommonResponse<List<UserOneTimeKeyResponse>>> getOneTimeKeys(@PathVariable String identityKey, @RequestParam Optional<UUID> userKey) {
		try {
			List<UserOneTimeKeyResponse> savedKeys = new ArrayList<>();			
			if (userKey.isPresent()) {
				savedKeys.add(keyService.getOneTimeKey(userKey.get(), identityKey));
			} else {
				savedKeys = keyService.getOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()), identityKey);
			}
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedKeys));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}	
	
	/**
	 * Retrieves remaining count of identityKey's unused one-time keys.
	 *
	 * @param identityKey the identity key whose one-time keys are requested
	 * @return a {@link CommonResponse} containing a list of {@link UserOneTimeKeyResponse}
	 */
	@Override
	@GetMapping("/identity/{identityKey}/count-one-time-keys")
	public ResponseEntity<CommonResponse<Integer>> getCountOneTimeKeys(@PathVariable String identityKey) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
					keyService.getCountOneTimeKeys(UUID.fromString(SecurityUtil.getUserKey()), identityKey)));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.IDENTITY_KEY_NOT_FOUND));
		}
	}	
	
	/**
	 * Deletes a single one-time key by its ID.
	 *
	 * @param id the ID of the one-time key to delete
	 * @return a {@link CommonResponse} indicating the deletion result
	 */
	@Override
	@DeleteMapping("/identity/one-time/{id}")
	public ResponseEntity<CommonResponse<?>> deleteOneTimeKey(@PathVariable Long id) {
		try {
			keyService.deleteOneTimeKey(id, UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.ONE_TIME_KEY_DELETE_SUCCESS));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ONE_TIME_KEY_ID_NOT_FOUND));
		}
	}	
	
	/**
	 * Deletes multiple one-time keys by their IDs.
	 *
	 * @param ids the list of one-time key IDs to delete
	 * @return a {@link CommonResponse} indicating the deletion result
	 */
	@Override
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
	
	/**
	 * Deletes all identity and one-time keys for the authenticated user.
	 *
	 * @return a {@link CommonResponse} indicating that all keys were deleted successfully
	 */
	@Override
	@DeleteMapping("/all")
	public ResponseEntity<CommonResponse<?>> deleteAllUserKeys() {
		keyService.deleteAll(UUID.fromString(SecurityUtil.getUserKey()));
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}
}
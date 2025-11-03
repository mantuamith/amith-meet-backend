package com.algomeet.signalingservice.controller;

import java.util.List;
import java.util.Optional;
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

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserSessionBackupRequest;
import com.algomeet.signalingservice.dto.UserSessionBackupResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.UserSessionBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

/**
 * REST controller that handles user session backup operations such as
 * saving, restoring, and deleting session backups.
 */
@RestController
@RequestMapping("/signaling/backup/sessions")
public class UserSessionBackupController {

	private final UserSessionBackupService service;

	public UserSessionBackupController(UserSessionBackupService service) {
		this.service = service;
	}

	/**
	 * Saves or updates a user session backup for the currently authenticated user.
	 *
	 * @param request the request payload containing session backup details
	 * @return a {@link ResponseEntity} containing a {@link CommonResponse} with the saved session data
	 */
	@PostMapping
	public ResponseEntity<CommonResponse<UserSessionBackupResponse>> saveBackup(@Validated @RequestBody UserSessionBackupRequest request) {
		UserSessionBackupResponse savedSession = service.saveBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedSession));
	}

	/**
	 * Restores a specific session backup for the currently authenticated user.
	 *
	 * @param sessionId the ID of the session to restore
	 * @return a {@link ResponseEntity} containing a {@link CommonResponse} with the restored session data,
	 *         or a NOT_FOUND response if the session does not exist
	 */
	@GetMapping("/{sessionId}/restore")
	public ResponseEntity<CommonResponse<UserSessionBackupResponse>> restoreSession(
			@PathVariable String sessionId) {

		Optional<UserSessionBackupResponse> result = service.restoreSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId);
		if (result.isPresent()) {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, result.get()));
		} else{
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_SESSION_BACKUP_NOT_FOUND));
		}        
	}

	/**
	 * Restores all session backups for the currently authenticated user.
	 *
	 * @return a {@link ResponseEntity} containing a {@link CommonResponse} with a list of restored sessions
	 */
	@GetMapping("/restore")
	public ResponseEntity<CommonResponse<List<UserSessionBackupResponse>>> restoreSessions(@RequestParam("deviceId") String deviceId) {
		List<UserSessionBackupResponse> sessions = service.restoreSessions(UUID.fromString(SecurityUtil.getUserKey()), deviceId);

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, sessions));
	}

	/**
	 * Deletes a specific session backup for the currently authenticated user.
	 *
	 * @param sessionId the ID of the session to delete
	 * @return a {@link ResponseEntity} containing a {@link CommonResponse} indicating success or failure
	 */
	@DeleteMapping("/{sessionId}")
	public ResponseEntity<CommonResponse<?>> deleteSession(
			@PathVariable String sessionId) {		
		try {
			service.deleteBySessionId(UUID.fromString(SecurityUtil.getUserKey()), sessionId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.USER_SESSION_BACKUP_DELETE_SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_SESSION_BACKUP_NOT_FOUND));
		}
	}
	
	@DeleteMapping("/all")
	public ResponseEntity<CommonResponse<?>> deleteSessions(
			@PathVariable String sessionId) {		
			service.deleteByUserKey(UUID.fromString(SecurityUtil.getUserKey()));
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}
}

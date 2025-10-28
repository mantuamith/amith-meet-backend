package com.algomeet.signalingservice.controller;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserSessionBackupRequest;
import com.algomeet.signalingservice.dto.UserSessionBackupResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.UserSessionBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/signaling/backup/sessions")
public class UserSessionBackupController {

	private final UserSessionBackupService service;

	public UserSessionBackupController(UserSessionBackupService service) {
		this.service = service;
	}

	/**
	 * Save or update a session backup.
	 */
	@PostMapping
	public ResponseEntity<CommonResponse<UserSessionBackupResponse>> saveBackup(@RequestBody UserSessionBackupRequest request) {
		UserSessionBackupResponse savedSession = service.saveBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedSession));
	}

	/**
	 * Restore a specific session by userKey and sessionId.
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
	 * Restore all sessions for a given userKey.
	 */
	@GetMapping("/restore")
	public ResponseEntity<CommonResponse<List<UserSessionBackupResponse>>> restoreSessions() {
		List<UserSessionBackupResponse> sessions = service.restoreSessions(UUID.fromString(SecurityUtil.getUserKey()));

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, sessions));
	}

	/**
	 * Delete a specific session backup.
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
}

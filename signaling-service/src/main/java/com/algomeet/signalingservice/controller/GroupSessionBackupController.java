package com.algomeet.signalingservice.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalingservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.MaxSessionsLimitExceededException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.GroupSessionBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

/**
 * REST controller for managing Matrix group session backups (both inbound and outbound).
 * <p>
 * Provides APIs to save, restore, and delete group session backups for encrypted Matrix communications.
 * Inbound sessions are used to decrypt received group messages, while outbound sessions are used to
 * encrypt messages sent to a group.
 */
@RestController
@RequestMapping("/signaling/backup/group-sessions")
@RequiredArgsConstructor
public class GroupSessionBackupController {

    private final GroupSessionBackupService service;

    // =====================================================
    // Inbound Group Session APIs
    // =====================================================

    /**
     * Saves an inbound group session backup for the currently authenticated user.
     * <p>
     * An inbound session corresponds to a received Megolm session from another device in a Matrix room.
     *
     * @param request the inbound group session data to be backed up
     * @return a {@link CommonResponse} containing the saved session details,
     *         or a service unavailable response if the maximum inbound session limit is exceeded
     */
    @PostMapping("/inbound")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveInboundBackup(
    		@Validated @RequestBody GroupSessionBackupRequest request) {
    	try {
    		GroupSessionBackupResponse response = service.saveInboundBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
    	} catch (MaxSessionsLimitExceededException ex) {
    		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
    				.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_MAX_INBOUND_SESSIONS_LIMIT_EXCEEDED));
    	}
    }

    /**
     * Restores all inbound group session backups for the current user.
     * <p>
     * This is typically used during app startup or session recovery to restore the ability
     * to decrypt past group messages.
     *
     * @return a {@link CommonResponse} containing a list of inbound group sessions
     */
    @GetMapping("/inbound/restore")
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getInboundSessions() {

        List<GroupSessionBackupResponse> sessions = service.restoreInboundSessions(UUID.fromString(SecurityUtil.getUserKey()));
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, sessions));
    }

    /**
     * Restores a specific inbound group session backup by session ID and ratchet index.
     * <p>
     * The ratchet index identifies a specific step in the Megolm message ratcheting process.
     *
     * @param sessionId     the group session ID
     * @param ratchetIndex  the ratchet index for the inbound session
     * @return a {@link CommonResponse} containing the matching session,
     *         or a not found response if no such record exists
     */
    @GetMapping("/{sessionId}/{ratchetIndex}/inbound/restore")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getInboundSession(
            @PathVariable String sessionId,
            @PathVariable Integer ratchetIndex) {

        Optional<GroupSessionBackupResponse> resultOpt =
                service.restoreInboundSession(UUID.fromString(SecurityUtil.getUserKey()), ratchetIndex, sessionId);
		
        return resultOpt.map(session -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, session)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                		.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND)));
    }

    /**
     * Deletes a specific inbound group session backup identified by session ID and ratchet index.
     *
     * @param sessionId     the group session ID
     * @param ratchetIndex  the ratchet index of the session to delete
     * @return a {@link CommonResponse} indicating success or not found
     */
    @DeleteMapping("/{sessionId}/{ratchetIndex}/inbound")
    public ResponseEntity<CommonResponse<?>> deleteInboundSession(
    		@PathVariable String sessionId,
    		@PathVariable int ratchetIndex) {
    	try {
    		service.deleteInboundSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId, ratchetIndex);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_DELETE_SUCCESS));
    	} catch (RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND));
    	}  
    }
    
    /**
     * Prunes old inbound group session backups for a given session ID, keeping only the most recent entries.
     * <p>
     * Useful for limiting storage usage while preserving the ability to decrypt recent messages.
     *
     * @param sessionId  the group session ID whose backups should be pruned
     * @param keepLastN  the number of most recent sessions to retain (must be greater than zero)
     * @return a {@link CommonResponse} indicating successful pruning
     * @throws RuntimeException if {@code keepLastN} is zero or invalid
     */
    @DeleteMapping("/{sessionId}/inbound/prune")
    public ResponseEntity<CommonResponse<?>> pruneInboundBackups(
    		@PathVariable String sessionId,
    		@RequestParam(defaultValue = "100") int keepLastN) {
    	if (keepLastN == 0) {
    		throw new RuntimeException("keepLastN value must be greater than 0");
    	}
    	
    	service.pruneInboundBackups(UUID.fromString(SecurityUtil.getUserKey()), sessionId, keepLastN);
    	return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }

    // =====================================================
    // Outbound Group Session APIs
    // =====================================================

    /**
     * Saves an outbound group session backup for the current user.
     * <p>
     * An outbound session is used to encrypt messages sent by the user in a Matrix room.
     *
     * @param request the outbound group session data to be backed up
     * @return a {@link CommonResponse} containing the saved session details,
     *         or a service unavailable response if the maximum outbound session limit is exceeded
     */
    @PostMapping("/outbound")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveOutboundBackup(
    		@Validated @RequestBody GroupSessionBackupRequest request) {

    	try {
    		GroupSessionBackupResponse response = service.saveOutboundBackup(UUID.fromString(SecurityUtil.getUserKey()), request);        
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
    	} catch (MaxSessionsLimitExceededException ex) {
    		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
    				.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_MAX_OUTBOUND_SESSIONS_LIMIT_EXCEEDED));
    	}
    }

    /**
     * Restores all outbound group session backups for the current user.
     * <p>
     * Typically used to restore encryption capability after application restart or device migration.
     *
     * @return a {@link CommonResponse} containing a list of outbound group sessions
     */
    @GetMapping("/outbound/restore")
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getOutboundSessions() {

        List<GroupSessionBackupResponse> sessions = service.restoreOutboundSessions(UUID.fromString(SecurityUtil.getUserKey()));
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, sessions));
    }

    /**
     * Restores a specific outbound group session backup identified by its session ID.
     *
     * @param sessionId  the group session ID
     * @return a {@link CommonResponse} containing the session details,
     *         or a not found response if the session does not exist
     */
    @GetMapping("/{sessionId}/outbound/restore")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getOutboundSession(
            @PathVariable String sessionId) {

        Optional<GroupSessionBackupResponse> resultOpt =
                service.restoreOutboundSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId);

        return resultOpt.map(session -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, session)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                		.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND)));
    }

    /**
     * Deletes a specific outbound group session backup identified by its session ID.
     *
     * @param sessionId  the group session ID
     * @return a {@link CommonResponse} indicating success or not found
     */
    @DeleteMapping("/{sessionId}/outbound")
    public ResponseEntity<CommonResponse<?>> deleteOutboundSession(
    		@PathVariable String sessionId) {
    	try {
    		service.deleteOutboundSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_DELETE_SUCCESS));
    	} catch (RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND));
    	}  
    }
}
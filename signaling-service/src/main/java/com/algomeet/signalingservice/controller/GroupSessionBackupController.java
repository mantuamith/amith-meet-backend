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
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.GroupSessionBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signaling/backup/group-sessions")
@RequiredArgsConstructor
public class GroupSessionBackupController {

    private final GroupSessionBackupService service;

    // =====================================================
    // Inbound Group Session APIs
    // =====================================================

    @PostMapping("/inbound")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveInboundBackup(
            @Validated @RequestBody GroupSessionBackupRequest request) {

        GroupSessionBackupResponse response = service.saveInboundBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
    }

    @GetMapping("/inbound/restore")
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getInboundSessions() {

        List<GroupSessionBackupResponse> sessions = service.restoreInboundSessions(UUID.fromString(SecurityUtil.getUserKey()));
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, sessions));
    }

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

    @PostMapping("/outbound")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveOutboundBackup(
             @Validated @RequestBody GroupSessionBackupRequest request) {

        GroupSessionBackupResponse response = service.saveOutboundBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
    }

    @GetMapping("/outbound/restore")
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getOutboundSessions() {

        List<GroupSessionBackupResponse> sessions = service.restoreOutboundSessions(UUID.fromString(SecurityUtil.getUserKey()));
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, sessions));
    }

    @GetMapping("/{sessionId}/outbound/restore")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getOutboundSession(
            @PathVariable String sessionId) {

        Optional<GroupSessionBackupResponse> resultOpt =
                service.restoreOutboundSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId);

        return resultOpt.map(session -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, session)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                		.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND)));
    }

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
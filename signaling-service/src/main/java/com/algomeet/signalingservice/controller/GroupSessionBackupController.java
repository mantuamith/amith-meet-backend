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

    @GetMapping("/inbound/{sessionId}/{ratchetIndex}/restore")
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getInboundSession(
            @PathVariable String sessionId,
            @PathVariable Integer ratchetIndex) {

        Optional<GroupSessionBackupResponse> resultOpt =
                service.restoreInboundSession(UUID.fromString(SecurityUtil.getUserKey()), ratchetIndex, sessionId);
		
        return resultOpt.map(session -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, session)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                		.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND)));
    }

    @DeleteMapping("/inbound/{sessionId}/{ratchetIndex}")
    public ResponseEntity<Void> deleteInboundSession(
            @PathVariable String sessionId,
            @PathVariable int ratchetIndex) {

        service.deleteInboundSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId, ratchetIndex);
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/inbound/{sessionId}/prune")
    public ResponseEntity<Void> pruneInboundBackups(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int keepLastN) {

        service.pruneInboundBackups(UUID.fromString(SecurityUtil.getUserKey()), sessionId, keepLastN);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // Outbound Group Session APIs
    // =====================================================

    @PostMapping("outbound")
    public ResponseEntity<GroupSessionBackupResponse> saveOutboundBackup(
             @Validated @RequestBody GroupSessionBackupRequest request) {

        GroupSessionBackupResponse response = service.saveOutboundBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/outbound/restore")
    public ResponseEntity<List<GroupSessionBackupResponse>> getOutboundSessions() {

        List<GroupSessionBackupResponse> sessions = service.restoreOutboundSessions(UUID.fromString(SecurityUtil.getUserKey()));
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/outbound/{sessionId}/restore")
    public ResponseEntity<GroupSessionBackupResponse> getOutboundSession(
            @PathVariable String sessionId) {

        Optional<GroupSessionBackupResponse> result =
                service.restoreOutboundSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId);

        return result.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/outbound/{sessionId}")
    public ResponseEntity<Void> deleteOutboundSession(
            @PathVariable String sessionId) {

        service.deleteOutboundSession(UUID.fromString(SecurityUtil.getUserKey()), sessionId);
        return ResponseEntity.noContent().build();
    }
}
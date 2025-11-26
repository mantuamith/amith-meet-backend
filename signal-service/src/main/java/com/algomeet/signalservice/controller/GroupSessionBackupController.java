package com.algomeet.signalservice.controller;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.service.GroupSessionBackupService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/signal/backup/group-sessions")
@RequiredArgsConstructor
public class GroupSessionBackupController {

    private final GroupSessionBackupService service;

    @PostMapping
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveBackup(
            @Validated @RequestBody GroupSessionBackupRequest request) {

        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
        GroupSessionBackupResponse saved = service.saveBackup(userKey, request);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getBackups() {
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
        List<GroupSessionBackupResponse> backups = service.findBackups(userKey);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, backups));
    }

    @GetMapping("/{distributionId}")
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getBackupByDistribution(
            @PathVariable UUID distributionId) {
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
        List<GroupSessionBackupResponse> backups = service.findBackupByDistribution(userKey, distributionId);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, backups));
    }

    @DeleteMapping("/{groupId}/{distributionId}")
    public ResponseEntity<CommonResponse<?>> deleteBackup(
            @PathVariable String groupId,
            @PathVariable UUID distributionId) {
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
        service.deleteBackup(userKey, groupId, distributionId);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }

    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> deleteAllBackups() {
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
        service.deleteAllUserBackups(userKey);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
}

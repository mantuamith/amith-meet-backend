package com.algomeet.signalingservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserAccountBackupRequest;
import com.algomeet.signalingservice.dto.UserAccountBackupResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.UserAccountBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

@RestController
@RequestMapping("/signaling/backup/user-account")
public class UserAccountBackupController {
    private final UserAccountBackupService service;

    public UserAccountBackupController(UserAccountBackupService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> saveBackup(@Validated @RequestBody UserAccountBackupRequest request) {
    	UserAccountBackupResponse saved = service.saveBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> getBackup() {
        return service.restoreBackup(UUID.fromString(SecurityUtil.getUserKey()))
                .map(account -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, account)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                		.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_NOT_FOUND)));
    }

    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> deleteBackup() {
    	try {
    		service.deleteBackup(UUID.fromString(SecurityUtil.getUserKey()));
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    	} catch (RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND)
    				.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_NOT_FOUND));
    	}
    }
}
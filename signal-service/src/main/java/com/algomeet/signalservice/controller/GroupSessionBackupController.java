package com.algomeet.signalservice.controller;

import com.algomeet.signalservice.controller.swagger.GroupSessionBackupControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSessionBackupService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/signal/backup/group-sessions")
@RequiredArgsConstructor
public class GroupSessionBackupController implements GroupSessionBackupControllerDoc{
	private final GroupSessionBackupService service;

	@Override
	@PostMapping
	public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveBackup(
			@Validated @RequestBody GroupSessionBackupRequest request) {

		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
		GroupSessionBackupResponse saved = service.saveBackup(userKey, request);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));
	}

	@Override
	@GetMapping
	public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getBackups() {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
		List<GroupSessionBackupResponse> backups = service.findBackups(userKey);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, backups));
	}

	@Override
	@GetMapping("/{groupId}/{distributionId}")
	public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getBackupByDistribution(
			@PathVariable String groupId,
			@PathVariable UUID distributionId) {
		try {
			UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
			GroupSessionBackupResponse backups = service.findBackup(userKey, groupId, distributionId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, backups));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND));
		}
	}

	@Override
	@GetMapping("/{deviceId}/device")
	public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getBackupByDevice(
			@PathVariable Integer deviceId) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
		List<GroupSessionBackupResponse> backups = service.findBackupByDevice(userKey, deviceId);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, backups));
	}

	@Override
	@DeleteMapping("/{groupId}/{distributionId}")
	public ResponseEntity<CommonResponse<?>> deleteBackup(
			@PathVariable String groupId,
			@PathVariable UUID distributionId) {
		try {
			UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
			service.deleteBackup(userKey, groupId, distributionId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND));
		}
	}

	@Override
	@DeleteMapping("/{deviceId}/device")
	public ResponseEntity<CommonResponse<?>> deleteBackupByDevice(
			@PathVariable Integer deviceId) {
		try {
			UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
			service.deleteBackupByDevice(userKey, deviceId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND));
		}
	}

	@Override
	@DeleteMapping
	public ResponseEntity<CommonResponse<?>> deleteAllBackups() {
		try {
			UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
			service.deleteAllUserBackups(userKey);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND));
		}
	}
}

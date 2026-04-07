package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.GroupSenderKeyBackupControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupUpdateRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.GroupSenderKeyBackupExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSenderKeyBackupService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal/group-sender-key-backups")
@RequiredArgsConstructor
public class GroupSenderKeyBackupController implements GroupSenderKeyBackupControllerDoc{
	private final GroupSenderKeyBackupService service;

	@Override
	@PostMapping
	public ResponseEntity<CommonResponse<GroupSenderKeyBackupResponse>> create(@Validated @RequestBody GroupSenderKeyBackupRequest request) {
		try {
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
					service.save(UUID.fromString(SecurityUtil.getUserKey()),  request)));
		} catch(GroupSenderKeyBackupExistsException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(
					CommonResponse.from(ResponseCode.GROUP_SENDER_KEY_BACKUP_EXISTS));
		}
	}
	
	@Override
	@PutMapping("/{groupId}/{distributionId}")
	public ResponseEntity<CommonResponse<GroupSenderKeyBackupResponse>> update(
			@PathVariable String groupId,
			@PathVariable UUID distributionId,
			@Validated @RequestBody GroupSenderKeyBackupUpdateRequest request) {
		
		try {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
				service.update(UUID.fromString(SecurityUtil.getUserKey()), groupId, distributionId, request)));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.GROUP_SENDER_KEY_BACKUP_NOT_FOUND));
		}
	}		

	@Override
	@GetMapping("/{groupId}/{distributionId}")
	public ResponseEntity<CommonResponse<GroupSenderKeyBackupResponse>> get(
			@PathVariable String groupId,
			@PathVariable UUID distributionId) {
		return service.findById(UUID.fromString(SecurityUtil.getUserKey()), groupId, distributionId)
				.map(data -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, data)))
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(CommonResponse.from(ResponseCode.GROUP_SENDER_KEY_BACKUP_NOT_FOUND)));
	}

	@Override
	@GetMapping
	public ResponseEntity<CommonResponse<List<GroupSenderKeyBackupResponse>>> getByUser() {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
				service.findByUser(UUID.fromString(SecurityUtil.getUserKey()))));
	}

	@Override
	@GetMapping("/{groupId}")
	public ResponseEntity<CommonResponse<List<GroupSenderKeyBackupResponse>>> getByGroup(@PathVariable String groupId) {
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
				service.findByGroup(groupId)));
	}

	@Override
	@DeleteMapping("/{groupId}/{distributionId}")
	public ResponseEntity<CommonResponse<?>> delete(
			@PathVariable String groupId,
			@PathVariable UUID distributionId) {
		try {
			service.delete(UUID.fromString(SecurityUtil.getUserKey()), groupId, distributionId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.GROUP_SENDER_KEY_BACKUP_NOT_FOUND));
		}
	}

}
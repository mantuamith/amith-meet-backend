package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSenderKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal/v2/devices")
@RequiredArgsConstructor
public class GroupSenderKeyController {

	private final GroupSenderKeyService service;

	/** Sender device uploads SKDM */
	@PostMapping("/{senderDeviceId}/groups/{groupId}/sender-keys")
	public ResponseEntity<CommonResponse<GroupSenderKeyResponse>> create(
			@PathVariable Integer senderDeviceId,
			@PathVariable String groupId,
			@RequestBody GroupSenderKeyRequest request) {

		UUID senderUserKey = UUID.fromString(SecurityUtil.getUserKey());

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
				service.create(senderUserKey, senderDeviceId, groupId, request))
				);
	}

	/** Fetch sender key for a device+group */
	@GetMapping("/{senderDeviceId}/groups/{groupId}/sender-keys")
	public ResponseEntity<CommonResponse<GroupSenderKeyResponse>> get(
			@PathVariable Integer senderDeviceId,
			@PathVariable String groupId) {

		UUID senderUserKey = UUID.fromString(SecurityUtil.getUserKey());
		try {
			GroupSenderKeyResponse response =
					service.get(senderUserKey, senderDeviceId, groupId);

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
					response));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
		}
	}

	/** Receiver device polls for SKDM */
	@GetMapping("/{receiverDeviceId}/groups/{groupId}/sender-keys/poll")
	public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> poll(
			@PathVariable Integer receiverDeviceId,
			@PathVariable String groupId,
	        @RequestParam(defaultValue = "30000") long timeoutMs) {
		try {
			UUID receiverUserKey = UUID.fromString(SecurityUtil.getUserKey());
			List<GroupSenderKeyResponse> list =
					service.longPoll(receiverUserKey, receiverDeviceId, groupId, timeoutMs);

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
					list));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
		}
	}	
}
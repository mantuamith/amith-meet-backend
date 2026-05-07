package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.GroupSenderKeyControllerDoc;
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
public class GroupSenderKeyController implements GroupSenderKeyControllerDoc{

	private final GroupSenderKeyService service;

	/** Sender device uploads SKDM */
	@Deprecated
	@PostMapping("/{senderDeviceId}/groups/{groupId}/sender-keys")
	public ResponseEntity<CommonResponse<GroupSenderKeyResponse>> create(
			@PathVariable Integer senderDeviceId,
			@PathVariable String groupId,
			@Validated @RequestBody GroupSenderKeyRequest request) {
		try {
			UUID senderUserKey = UUID.fromString(SecurityUtil.getUserKey());

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
					service.create(senderUserKey, senderDeviceId, groupId, request))
					);
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}

	/** Fetch sender key for a device+group */
	@Deprecated
	@GetMapping("/{senderDeviceId}/groups/{groupId}/sender-keys")
	public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> get(
			@PathVariable Integer senderDeviceId,
			@PathVariable String groupId) {

		UUID senderUserKey = UUID.fromString(SecurityUtil.getUserKey());
		try {
			List<GroupSenderKeyResponse> list =
					service.getList(senderUserKey, senderDeviceId, groupId);

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
					list));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}
	
	/** Receiver device polls for SKDM */
	@Deprecated
	@GetMapping("/{receiverDeviceId}/groups/{groupId}/sender-keys/poll")
	public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> poll(
			@PathVariable Integer receiverDeviceId,
			@PathVariable String groupId,
	        @RequestParam(defaultValue = "0") long timeoutMs) {
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
	
	/** Receiver device polls for SKDM */
	@Deprecated
	@GetMapping("/{receiverDeviceId}/groups/{groupId}/sender-keys/receiver")
	public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> getSenderKeys(
			@PathVariable Integer receiverDeviceId,
			@PathVariable String groupId) {
		try {
			UUID receiverUserKey = UUID.fromString(SecurityUtil.getUserKey());
			List<GroupSenderKeyResponse> list =
					service.getSenderKeys(receiverUserKey, receiverDeviceId, groupId);

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
					list));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
		}
	}	
}
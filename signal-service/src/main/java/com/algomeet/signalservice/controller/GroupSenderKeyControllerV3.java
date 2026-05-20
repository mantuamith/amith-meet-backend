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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.GroupSenderKeyControllerV3Doc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.entity.GroupSenderKeyId;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSenderKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal/v3/groups/{groupId}/sender-keys")
@RequiredArgsConstructor
public class GroupSenderKeyControllerV3 implements GroupSenderKeyControllerV3Doc {

    private final GroupSenderKeyService service;

    private UUID currentUserKey() {
        return UUID.fromString(SecurityUtil.getUserKey());
    }

    /** Sender uploads SKDM */
    @PostMapping("/devices/{senderDeviceId}")
    public ResponseEntity<CommonResponse<GroupSenderKeyResponse>> create(
            @PathVariable UUID groupId,
            @PathVariable Integer senderDeviceId,
            @Validated @RequestBody GroupSenderKeyRequest request) {

        try {
            return ResponseEntity.ok(
                    CommonResponse.from(
                            ResponseCode.SUCCESS,
                            service.create(currentUserKey(), senderDeviceId, groupId, request)
                    )
            );
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
        }
    }

    /** Fetch sender key for sender device */
    @GetMapping("/devices/{senderDeviceId}")
    public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> get(
            @PathVariable UUID groupId,
            @PathVariable Integer senderDeviceId) {

        try {
            return ResponseEntity.ok(
                    CommonResponse.from(
                            ResponseCode.SUCCESS,
                            service.getList(currentUserKey(), senderDeviceId, groupId)
                    )
            );
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
        }
    }

    /** Receiver fetch inbox sender-keys */
    @GetMapping("/devices/{receiverDeviceId}/receiver")
    public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> getSenderKeys(
            @PathVariable UUID groupId,
            @PathVariable Integer receiverDeviceId) {

        try {
            return ResponseEntity.ok(
                    CommonResponse.from(
                            ResponseCode.SUCCESS,
                            service.getSenderKeys(currentUserKey(), receiverDeviceId, groupId)
                    )
            );
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
        }
    }
    
    /** Missing sender-key devices (important optimization endpoint) */
    @GetMapping("/devices/missing")
    public ResponseEntity<CommonResponse<List<UserDeviceResponse>>> getMissingSenderKeys(
            @PathVariable UUID groupId) {

        try {
            return ResponseEntity.ok(
                    CommonResponse.from(
                            ResponseCode.SUCCESS,
                            service.getMissingDevices(currentUserKey(), groupId)
                    )
            );
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
        }
    }

    /** Delete sender key mapping */
    @DeleteMapping("/devices/{senderDeviceId}")
    public ResponseEntity<CommonResponse<?>> deleteBySender(
            @PathVariable UUID groupId,
            @PathVariable Integer senderDeviceId,
            @RequestParam UUID receiverUserKey,
            @RequestParam Integer receiverDeviceId) {

        try {
            UUID senderUserKey = currentUserKey();

            service.delete(new GroupSenderKeyId(
                    senderUserKey,
                    senderDeviceId,
                    receiverUserKey,
                    receiverDeviceId,
                    groupId
            ));

            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
        }
    }

    /** Receiver soft delete */
    @DeleteMapping("/devices/{receiverDeviceId}/receiver")
    public ResponseEntity<CommonResponse<?>> markAsProcessed(
            @PathVariable UUID groupId,
            @PathVariable Integer receiverDeviceId,
            @RequestParam UUID senderUserKey,
            @RequestParam Integer senderDeviceId) {

        try {
            UUID receiverUserKey = currentUserKey();

            service.markAsProcessed(new GroupSenderKeyId(
                    senderUserKey,
                    senderDeviceId,
                    receiverUserKey,
                    receiverDeviceId,
                    groupId
            ));

            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
        }
    }
    
    /** Delete receiver member key mapping */
    @DeleteMapping("/members/{receiverUserKey}")
    public ResponseEntity<CommonResponse<?>> deleteSenderKeys(
            @PathVariable UUID groupId,
            @PathVariable UUID receiverUserKey) {

        try {
            UUID senderUserKey = currentUserKey();

            service.delete(senderUserKey, receiverUserKey, groupId);

            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
        }
    }       
    
    /** delete */
    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> delete(
            @PathVariable UUID groupId) {
        try {
            UUID receiverUserKey = currentUserKey();
            // Delete group sender keys
            service.delete(receiverUserKey.toString(), groupId);

            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND));
        }
    }
}
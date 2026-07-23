package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.GroupSenderKeyControllerV4Doc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSenderKeyService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal/v4/groups/{groupId}/sender-keys")
@RequiredArgsConstructor
public class GroupSenderKeyControllerV4 implements GroupSenderKeyControllerV4Doc {

    private final GroupSenderKeyService service;

    private UUID currentUserKey() {
        return UUID.fromString(SecurityUtil.getUserKey());
    }

    /** Sender uploads SKDM */
    @PostMapping("/devices/{senderDeviceId}")
    public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> create(
            @PathVariable UUID groupId,
            @PathVariable Integer senderDeviceId,
            @Validated @RequestBody List<GroupSenderKeyRequest> requests) {

        try {
			
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS,
					service.create(currentUserKey(), senderDeviceId, groupId, requests)
					));
        } catch (RecordNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
        }
    }
}
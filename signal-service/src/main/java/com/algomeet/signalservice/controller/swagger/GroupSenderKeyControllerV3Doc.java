package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.dto.UserDeviceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Group Sender Keys V3",
    description = "Manage Signal-style sender key distribution messages (SKDM) within groups per device"
)
public interface GroupSenderKeyControllerV3Doc {

    @Operation(
        summary = "Create sender-key (SKDM)",
        description = "Sender device uploads encrypted sender-key distribution message for a group"
    )
    ResponseEntity<CommonResponse<GroupSenderKeyResponse>> create(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Sender device ID") Integer senderDeviceId,
            GroupSenderKeyRequest request
    );

    @Operation(
        summary = "Get sender-key for sender device",
        description = "Retrieve all sender-key messages created by a sender device in a group"
    )
    ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> get(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Sender device ID") Integer senderDeviceId
    );

    @Operation(
        summary = "Get devices missing sender-key distribution",
        description = """
            Returns group members' devices that have NOT yet received sender-key distribution.
            Used to avoid duplicate SKDM generation and reduce encryption overhead.
        """
    )
    ResponseEntity<CommonResponse<List<UserDeviceResponse>>> getMissingSenderKeys(
            @Parameter(description = "Group ID") String groupId
    );

    @Operation(
        summary = "Get sender-key inbox for receiver device",
        description = "Receiver device fetches all sender-key messages intended for it"
    )
    ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> getSenderKeys(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Receiver device ID") Integer receiverDeviceId
    );

    @Operation(
        summary = "Delete sender-key mapping (sender side)",
        description = "Hard deletes sender-key mapping between sender and receiver device"
    )
    ResponseEntity<CommonResponse<?>> deleteBySender(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Sender device ID") Integer senderDeviceId,
            @Parameter(description = "Receiver user key") UUID receiverUserKey,
            @Parameter(description = "Receiver device ID") Integer receiverDeviceId
    );

    @Operation(
        summary = "Soft delete sender-key mapping (receiver side)",
        description = "Marks sender-key mapping as deleted for receiver device only (soft delete)"
    )
    ResponseEntity<CommonResponse<?>> markAsProcessed(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Receiver device ID") Integer receiverDeviceId,
            @Parameter(description = "Sender user key") UUID senderUserKey,
            @Parameter(description = "Sender device ID") Integer senderDeviceId
    );
}
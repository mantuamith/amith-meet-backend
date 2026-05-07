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

@Tag(name = "Group Sender Keys (V3)", description = "APIs for managing Signal group sender keys per device")
public interface GroupSenderKeyControllerV3Doc {

    @Operation(
            summary = "Create sender-key distribution message (SKDM)",
            description = "Sender device uploads encrypted sender-key distribution message for a group"
    )
    ResponseEntity<CommonResponse<GroupSenderKeyResponse>> create(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Sender device ID") Integer senderDeviceId,
            GroupSenderKeyRequest request
    );

    @Operation(
            summary = "Get sender-key mappings for sender device",
            description = "Fetch all sender-key records created by a sender device for a group"
    )
    ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> get(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Sender device ID") Integer senderDeviceId
    );

    @Operation(
            summary = "Get devices missing sender-key distribution",
            description = """
                    Returns devices in the group that have NOT received sender-key distribution messages.
                    Used for optimization: prevents duplicate SKDM encryption + resend.
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
            description = "Hard delete sender-key mapping for a receiver device"
    )
    ResponseEntity<CommonResponse<?>> deleteBySender(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Sender device ID") Integer senderDeviceId,
            @Parameter(description = "Receiver user key") UUID receiverUserKey,
            @Parameter(description = "Receiver device ID") Integer receiverDeviceId
    );

    @Operation(
            summary = "Soft delete sender-key mapping (receiver side)",
            description = "Marks sender-key mapping as deleted for receiver device only"
    )
    ResponseEntity<CommonResponse<?>> deleteByReceiver(
            @Parameter(description = "Group ID") String groupId,
            @Parameter(description = "Sender user key") UUID senderUserKey,
            @Parameter(description = "Sender device ID") Integer senderDeviceId,
            @Parameter(description = "Receiver device ID") Integer receiverDeviceId
    );
}

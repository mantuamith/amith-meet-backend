package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.dto.UserDeviceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

public interface GroupSenderKeyControllerV3Doc {

    @Operation(
            summary = "Upload group sender keys",
            description = """
                    Upload encrypted Sender Key Distribution Messages (SKDM)
                    for a sender device to all intended receiver devices in the group.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sender keys uploaded successfully"),
            @ApiResponse(responseCode = "404", description = "Sender device not found")
    })
    ResponseEntity<CommonResponse<GroupSenderKeyResponse>> create(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId,

            @Parameter(description = "Sender device ID")
            @PathVariable Integer senderDeviceId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Encrypted sender key payload",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = GroupSenderKeyRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "items": [
                                        {
                                          "receiverUserKey": "2fc35cae-e0b7-40a5-b2aa-e86206730e99",
                                          "receiverDeviceId": 1,
                                          "skdmCipher": "BASE64_ENCRYPTED_SKDM"
                                        }
                                      ]
                                    }
                                    """)
                    )
            )
            @RequestBody GroupSenderKeyRequest request);

    @Operation(
            summary = "Get uploaded sender keys by sender device",
            description = """
                    Retrieve sender key mappings created by the authenticated sender device
                    within the specified group.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sender keys retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Sender device not found")
    })
    ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> get(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId,

            @Parameter(description = "Sender device ID")
            @PathVariable Integer senderDeviceId);

    @Operation(
            summary = "Fetch receiver inbox sender keys",
            description = """
                    Retrieve pending encrypted sender keys for the receiver device.
                    Used during group encryption synchronization.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sender keys retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Sender keys not found")
    })
    ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> getSenderKeys(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId,

            @Parameter(description = "Receiver device ID")
            @PathVariable Integer receiverDeviceId);

    @Operation(
            summary = "Get devices missing sender keys",
            description = """
                    Returns devices that are still missing sender keys
                    for the specified group.
                    Used as an optimization endpoint before uploading SKDM payloads.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Missing devices retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User device not found")
    })
    ResponseEntity<CommonResponse<List<UserDeviceResponse>>> getMissingSenderKeys(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId);

    @Operation(
            summary = "Delete sender key mapping",
            description = """
                    Permanently delete a specific sender key mapping
                    between sender and receiver devices.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sender key mapping deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Sender key mapping not found")
    })
    ResponseEntity<CommonResponse<?>> deleteBySender(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId,

            @Parameter(description = "Sender device ID")
            @PathVariable Integer senderDeviceId,

            @Parameter(description = "Receiver user key")
            @RequestParam UUID receiverUserKey,

            @Parameter(description = "Receiver device ID")
            @RequestParam Integer receiverDeviceId);

    @Operation(
            summary = "Mark sender key as processed",
            description = """
                    Receiver marks a sender key as processed or consumed.
                    Typically used after successful local decryption/import.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sender key marked as processed"),
            @ApiResponse(responseCode = "404", description = "Sender key mapping not found")
    })
    ResponseEntity<CommonResponse<?>> markAsProcessed(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId,

            @Parameter(description = "Receiver device ID")
            @PathVariable Integer receiverDeviceId,

            @Parameter(description = "Sender user key")
            @RequestParam UUID senderUserKey,

            @Parameter(description = "Sender device ID")
            @RequestParam Integer senderDeviceId);

    @Operation(
            summary = "Delete sender keys for removed group member",
            description = """
                    Delete all sender key mappings associated with
                    the specified receiver member in the group.
                    Commonly used when a member leaves or is removed from the room.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Member sender keys deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Sender keys not found")
    })
    ResponseEntity<CommonResponse<?>> deleteSenderKeys(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId,

            @Parameter(description = "Receiver user key")
            @PathVariable UUID receiverUserKey);

    @Operation(
            summary = "Delete all current user sender keys in group",
            description = """
                    Delete all sender keys associated with the authenticated user
                    for the specified group.
                    Usually triggered when leaving a group or resetting encryption state.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group sender keys deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Sender keys not found")
    })
    ResponseEntity<CommonResponse<?>> delete(
            @Parameter(description = "Group identifier")
            @PathVariable String groupId);
}
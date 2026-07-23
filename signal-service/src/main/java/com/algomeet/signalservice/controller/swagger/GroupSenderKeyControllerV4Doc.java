package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

public interface GroupSenderKeyControllerV4Doc {

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
    ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> create(
            @Parameter(description = "Group identifier")
            @PathVariable UUID groupId,

            @Parameter(description = "Sender device ID")
            @PathVariable Integer senderDeviceId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
            		description = "Encrypted sender key payload",
            		required = true,
            		content = @Content(
            				array = @ArraySchema(
            						schema = @Schema(implementation = GroupSenderKeyRequest.class)
            						)
            				)
            		)
            @RequestBody List<GroupSenderKeyRequest> requests);   
}
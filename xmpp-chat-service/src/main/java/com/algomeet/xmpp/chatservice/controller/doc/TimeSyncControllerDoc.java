package com.algomeet.xmpp.chatservice.controller.doc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Time Synchronization", description = "APIs for client and server time synchronization")
public interface TimeSyncControllerDoc {

    @GetMapping("/sync")
    @Operation(
        summary = "Get server timestamp",
        description = """
            Returns the current server time in Unix epoch milliseconds.

            This endpoint is used by clients to:
            - synchronize device time with the server
            - calculate clock drift
            - support accurate message ordering
            - improve read and delivery acknowledgement consistency
            - support timestamp validation for chat messages
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Server timestamp retrieved successfully",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = CommonResponse.class),
            examples = @ExampleObject(
                value = """
                {
                  "code": "SUCCESS",
                  "message": "Success",
                  "data": 1747384935123
                }
                """
            )
        )
    )
    public ResponseEntity<CommonResponse<Long>> getServerTime();
}


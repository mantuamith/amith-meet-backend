package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;


@Tag(
    name = "Recent Contacts",
    description = "APIs for retrieving recently interacted contacts based on chat activity and unread messages"
)
public interface ContactControllerDoc {

    /**
     * Retrieves a paginated list of recent contact IDs.
     */
    @Operation(
        summary = "Get recent contacts",
        description = "Returns a deduplicated list of recent contact IDs based on unread messages and chat interactions. "
                    + "Composite conversation IDs (e.g., userA_userB) are split into individual user keys."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved recent contacts",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CommonResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    @GetMapping
    public Mono<ResponseEntity<CommonResponse<List<String>>>> getRecentContacts(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,

            @Parameter(description = "Number of records per page", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size);
}
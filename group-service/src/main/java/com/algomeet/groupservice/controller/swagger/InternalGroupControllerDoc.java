package com.algomeet.groupservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Internal Group API",
    description = "Internal APIs for retrieving group information. Not intended for public use."
)

public interface InternalGroupControllerDoc {

    @Operation(
        summary = "Get group by ID",
        description = "Returns group details for internal service-to-service communication"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Group found and returned"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public Object getGroup(
            @Parameter(
                description = "Unique identifier of the group",
                example = "11111111-1111-1111-1111-111111111111",
                required = true
            )
            @PathVariable UUID groupId);

    @Operation(
        summary = "Get groups by member username",
        description = "Returns all groups that contain the given username for internal service-to-service communication"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Groups returned successfully")
    })
    public List<?> getGroupsForUsername(
            @Parameter(
                description = "Username of the member",
                example = "puneethaf",
                required = true
            )
            @PathVariable String username);
    
    @Operation(
            summary = "Clear group conversation history",
            description = """
                    Clears the requesting member's visible group conversation history.

                    This operation updates the member-specific history cutoff timestamp,
                    causing historical messages generated before the cutoff to be excluded
                    from future synchronization and conversation timeline retrieval operations.

                    The operation only affects the requesting member visibility context
                    and does not permanently delete group messages from the server.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group conversation history timeline cleared successfully"),
            @ApiResponse(responseCode = "404", description = "Target group ID not found"),
            @ApiResponse(responseCode = "409", description = "Conflict: Requesting user is not a verified member of this group"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    }) 
    public Boolean clearMemberHistoryTimeline(
            @Parameter(
                    description = "Target group identifier",
                    required = true,
                    example = "289c5f4d-58a0-4def-bf5b-0fd15c045575")
            @PathVariable UUID groupId,
            
            @Parameter(
                    description = "The unique user key identifier of the group member whose history timeline is being cleared.",
                    required = true,
                    example = "019e5eae-7c83-78ab-8aa8-e39c7e39f59c")
            @PathVariable(name = "userKey") UUID targetUserKey,
            
            @Parameter(
                    description = "Optional Unix epoch timestamp threshold in milliseconds. " +
                                  "If omitted, defaults to the current server system time.",
                    required = false,
                    example = "1779703910000")
            @RequestParam(name = "historyCutoff", required = false) Long historyCutoff);
    
    
    /**
     * Swagger documentation for updating chat group retention settings.
     */
    @Operation(
        summary = "Update group message retention policy",
        description = "Updates the number of days messages are retained before automatic deletion. " +
                      "This operation requires Group OWNER or ADMIN privileges.",
        parameters = {
            @Parameter(
                name = "groupId", 
                description = "The unique UUID identifier of the chat group", 
                required = true, 
                example = "123e4567-e89b-12d3-a456-426614174000"
            ),
            @Parameter(
                name = "messageRetentionDays", 
                description = "Number of days to retain messages. Use -1 for infinite retention.", 
                required = true, 
                example = "30"
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Retention policy successfully updated"
        ),
        @ApiResponse(
            responseCode = "403", 
            description = "Forbidden - User does not have OWNER or ADMIN roles in this group"
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Not Found - The specified group ID does not exist"
        )
    })
    Boolean updateGroupRetention(
            UUID groupId,
            UUID targetUserKey, // Hidden or marked deprecated if redundant in future iterations
            Integer messageRetentionDays
    );
}

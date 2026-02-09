package com.algomeet.groupservice.controller.swagger;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.CommonResponse;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(
    name = "Group Management",
    description = "APIs for creating groups, joining, leaving, and managing group members"
)
public interface GroupControllerDoc {
    @Operation(
        summary = "Create a new group",
        description = "Creates a new group and automatically adds the authenticated user as a member"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Group created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<CommonResponse<GroupResponse>> createGroup(
            @Valid @RequestBody GroupRequest groupRequest,
            Authentication authentication);
    
    @Operation(
    		summary = "Delete a group",
    		description = "Deletes a group by its ID"
    		)
    @ApiResponses({
    	@ApiResponse(responseCode = "200", description = "Group deleted successfully"),
    	@ApiResponse(responseCode = "404", description = "Group not found"),
    	@ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<CommonResponse<?>> removeGroup(
    		@Parameter(
    				description = "Unique identifier of the group",
    				example = "1",
    				required = true
    				)
    		@PathVariable Long groupId);

    @Operation(
        summary = "Join a group",
        description = "Adds the authenticated user to the specified group"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully joined the group"),
        @ApiResponse(responseCode = "404", description = "Group not found"),
        @ApiResponse(responseCode = "409", description = "User is already a group member")
    })
    public ResponseEntity<CommonResponse<?>> joinGroup(
            @Parameter(description = "Group ID", example = "1")
            @PathVariable Long groupId,
            Authentication authentication);

    @Operation(
        summary = "Add members to a group",
        description = "Adds multiple users to a group using their userKeys (UUIDs)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Members added successfully"),
        @ApiResponse(responseCode = "404", description = "Group not found"),
        @ApiResponse(responseCode = "409", description = "All users are already members")
    })
    public ResponseEntity<CommonResponse<?>> addGroupMembers(
            @Parameter(description = "Group ID", example = "1")
            @PathVariable Long groupId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "List of userKeys to add to the group",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = AddGroupMembersRequest.class)
                )
            )
            @Valid @RequestBody AddGroupMembersRequest request);
    
    @Operation(
        summary = "Leave a group",
        description = "Removes the authenticated user from the group"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully left the group"),
        @ApiResponse(responseCode = "404", description = "Group or member not found")
    })
    public ResponseEntity<CommonResponse<?>> leaveGroup(
            @Parameter(description = "Group ID", example = "1")
            @PathVariable Long groupId,
            Authentication authentication);

    @Operation(
        summary = "Get my groups",
        description = "Returns all groups where the authenticated user is a member"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Groups retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<CommonResponse<?>> getMyGroups(Authentication authentication);
    
    @Operation(
    	    summary = "Remove a member from a group",
    	    description = "Removes a specific member from a group using the member userKey"
    	)
    	@ApiResponses({
    	    @ApiResponse(responseCode = "200", description = "Member removed successfully"),
    	    @ApiResponse(responseCode = "404", description = "Group or member not found"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized")
    	})    
    public ResponseEntity<CommonResponse<?>> removeGroupMember(
    		@Parameter(
    				description = "Unique identifier of the group",
    				example = "1",
    				required = true
    				)
    		@PathVariable Long groupId,

    		@Parameter(
    				description = "User key (UUID) of the member to remove",
    				example = "550e8400-e29b-41d4-a716-446655440000",
    				required = true
    				)
    		@RequestParam String userKey);
}

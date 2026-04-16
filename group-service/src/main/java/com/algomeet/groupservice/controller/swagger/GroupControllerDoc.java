package com.algomeet.groupservice.controller.swagger;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.CommonResponse;
import com.algomeet.groupservice.dto.GroupInviteLinkResponse;
import com.algomeet.groupservice.dto.GroupPermissionsPatchRequest;
import com.algomeet.groupservice.dto.GroupPermissionsResponse;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.UpdateGroupRequest;

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
    description = "APIs for creating groups, joining, leaving, managing group members, and updating role permissions"
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
            summary = "Get group by ID",
            description = "Retrieve a group and its details using the group ID"
        )
        @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200",
                description = "Group retrieved successfully",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = GroupResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group ID not found",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonResponse.class)
                )
            )
        })
        public ResponseEntity<CommonResponse<GroupResponse>> getGroup(
                @Parameter(description = "Unique ID of the group", example = "11111111-1111-1111-1111-111111111111")
                @PathVariable UUID groupId);

        @Operation(
            summary = "Update group",
            description = "Update group details such as name or members using the group ID"
        )
        @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200",
                description = "Group updated successfully",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = GroupResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid request body",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Group ID not found",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CommonResponse.class)
                )
            )
        })
        public ResponseEntity<CommonResponse<GroupResponse>> updateGroup(
                @Parameter(description = "Unique ID of the group", example = "11111111-1111-1111-1111-111111111111")
                @PathVariable UUID groupId,

                @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Group update request payload",
                    required = true,
                    content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = UpdateGroupRequest.class)
                    )
                )
                @Valid @RequestBody UpdateGroupRequest request);

        @Operation(
            summary = "Partially update group role permissions",
            description = """
                    Uses PATCH merge semantics. Only the supplied roles and permission fields are updated.
                    This endpoint does not replace the full role-permission matrix.
                    Supported roles for this contract are OWNER, ADMIN, and MEMBER.
                    """
        )
        @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200",
                description = "Group role permissions updated successfully",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = GroupPermissionsResponse.class)
                )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid patch request or unsupported role"),
            @ApiResponse(responseCode = "403", description = "User is not allowed to update role permissions"),
            @ApiResponse(responseCode = "404", description = "Group ID not found")
        })
        public ResponseEntity<CommonResponse<GroupPermissionsResponse>> patchGroupPermissions(
                @Parameter(description = "Unique ID of the group", example = "11111111-1111-1111-1111-111111111111")
                @PathVariable UUID groupId,

                @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = """
                            Partial role-permission update request.
                            Example:
                            {
                              "rolePermissions": {
                                "ADMIN": {
                                  "approveNewMembers": true
                                },
                                "MEMBER": {
                                  "sendNewMessages": false
                                }
                              }
                            }
                            """,
                    required = true,
                    content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = GroupPermissionsPatchRequest.class)
                    )
                )
                @Valid @RequestBody GroupPermissionsPatchRequest request);
        
    
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
    				example = "11111111-1111-1111-1111-111111111111",
    				required = true
    				)
    		@PathVariable UUID groupId);

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
            @Parameter(description = "Group ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID groupId,
            @RequestParam Optional<String> nickname,
            Authentication authentication);

    @Operation(
        summary = "Join a group using invite code",
        description = "Adds the authenticated user to the specified group after validating the invite code"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully joined the group"),
        @ApiResponse(responseCode = "400", description = "Invite code is invalid"),
        @ApiResponse(responseCode = "404", description = "Group not found"),
        @ApiResponse(responseCode = "409", description = "User is already a group member")
    })
    public ResponseEntity<CommonResponse<?>> joinGroupByInvite(
            @Parameter(description = "Group ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID groupId,
            @Parameter(description = "Invite code for the group", example = "abc123")
            @RequestParam String inviteCode,
            @RequestParam Optional<String> nickname,
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
            @Parameter(description = "Group ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID groupId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "List of userKeys to add to the group",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = AddGroupMembersRequest.class)
                )
            )
            @Valid @RequestBody AddGroupMembersRequest request);

    @Operation(
        summary = "Get or generate a group invite link",
        description = "Returns the current invite link for a group, generating a new invite code if one does not exist yet"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invite link retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Group not found"),
        @ApiResponse(responseCode = "403", description = "User is not allowed to access the invite link")
    })
    public ResponseEntity<CommonResponse<GroupInviteLinkResponse>> getInviteLink(
            @Parameter(description = "Group ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID groupId);

    @Operation(
        summary = "Reset a group invite link",
        description = "Rotates the group invite code and returns a new invite link. Previously issued links become invalid"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invite link reset successfully"),
        @ApiResponse(responseCode = "404", description = "Group not found"),
        @ApiResponse(responseCode = "403", description = "User is not allowed to reset the invite link")
    })
    public ResponseEntity<CommonResponse<GroupInviteLinkResponse>> resetInviteLink(
            @Parameter(description = "Group ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID groupId);
    
    @Operation(
        summary = "Leave a group",
        description = "Removes the authenticated user from the group"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully left the group"),
        @ApiResponse(responseCode = "404", description = "Group or member not found")
    })
    public ResponseEntity<CommonResponse<?>> leaveGroup(
            @Parameter(description = "Group ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID groupId,
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
    				example = "11111111-1111-1111-1111-111111111111",
    				required = true
    				)
    		@PathVariable UUID groupId,

    		@Parameter(
    				description = "User key (UUID) of the member to remove",
    				example = "550e8400-e29b-41d4-a716-446655440000",
    				required = true
    				)
    		@RequestParam String userKey);
}

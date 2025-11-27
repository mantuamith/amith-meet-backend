package com.algomeet.meetservice.controller.swagger;

import com.algomeet.meetservice.Dto.ApproveRejectRequest;
import com.algomeet.meetservice.Dto.EditMeetingRequest;
import com.algomeet.meetservice.Dto.MeetingDto;
import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.Dto.MeetingResponse;
import com.algomeet.meetservice.Dto.OpenJoinRequest;
import com.algomeet.meetservice.model.Meeting;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


@Tag(name = "Meetings", description = "Endpoints for creating, joining, and managing meetings")
public interface MeetingControllerDoc {

    // ---------- CREATE ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "createMeeting",
            summary = "Create meeting",
            description = "**POST /api/meetings/create** — Creates a new meeting owned by the current user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Meeting created",
                            content = @Content(schema = @Schema(implementation = MeetingDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "500", description = "Server error",
                            content = @Content(examples = @ExampleObject(
                                    name = "InternalError",
                                    value = """
                    { "success": false, "code": "INTERNAL_ERROR", "message": "Unexpected error while fetching meeting" }
                    """
                            )))
            }
    )
    ResponseEntity<MeetingDto> createMeeting(@Valid @org.springframework.web.bind.annotation.RequestBody MeetingRequest request);

    // ---------- GET BY ID (authorized) ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "getMeeting",
            summary = "Get meeting",
            description = """
            **GET /api/meetings/{id}?token=...**  
            Fetch by id for host or authorized attendee. Optional join token allowed.
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Fetched",
                            content = @Content(schema = @Schema(implementation = MeetingResponse.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Started",
                                                    value = """
                            {
                              "success": true,
                              "code": "MEETING_JOINED_SUCCESS",
                              "message": "You can join now.",
                              "data": { "id": "m_123", "status": "STARTED" }
                            }
                            """
                                            ),
                                            @ExampleObject(
                                                    name = "Scheduled",
                                                    value = """
                            {
                              "success": true,
                              "code": "MEETING_NOT_STARTED",
                              "message": "Host hasn’t started the meeting yet.",
                              "data": { "id": "m_123", "status": "SCHEDULED" }
                            }
                            """
                                            ),
                                            @ExampleObject(
                                                    name = "Completed",
                                                    value = """
                            {
                              "success": true,
                              "code": "MEETING_COMPLETED",
                              "message": "This meeting is over.",
                              "data": { "id": "m_123", "status": "COMPLETED" }
                            }
                            """
                                            ),
                                            @ExampleObject(
                                                    name = "Expired",
                                                    value = """
                            {
                              "success": true,
                              "code": "MEETING_EXPIRED",
                              "message": "This meeting link has expired.",
                              "data": { "id": "m_123", "status": "EXPIRED" }
                            }
                            """
                                            )
                                    })),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(examples = @ExampleObject(
                                    name = "AccessDenied",
                                    value = """
                    {
                      "success": false,
                      "code": "MEETING_ACCESS_DENIED",
                      "message": "Unauthorized, invalid token, or meeting unavailable"
                    }
                    """
                            ))),
                    @ApiResponse(responseCode = "500", description = "Server error",
                            content = @Content(examples = @ExampleObject(
                                    name = "InternalError",
                                    value = """
                    { "success": false, "code": "INTERNAL_ERROR", "message": "Unexpected error while fetching meeting" }
                    """
                            )))
            }
    )
    ResponseEntity<MeetingResponse<MeetingDto>> getMeeting(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String id,
            @Parameter(in = ParameterIn.QUERY, description = "Optional join token")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String token
    );

    // ---------- OPEN JOIN (guest) ----------
    @Operation(
            operationId = "openJoin",
            summary = "Guest join via open link",
            description = """
            **POST /api/meetings/open/{id}/join**  
            Guests join an open meeting with link token; validates password when required and mints a non-moderator JWT.
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Joined",
                            content = @Content(schema = @Schema(implementation = MeetingResponse.class),
                                    examples = @ExampleObject(
                                            name = "JoinSuccess",
                                            value = """
                        {
                          "success": true,
                          "code": "MEETING_JOINED_SUCCESS",
                          "message": "You can join now.",
                          "data": {
                            "meeting": { "id": "m_123", "status": "STARTED" },
                            "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                            "room": "room_abc",
                            "exp": "2024-06-23T12:34:56Z"
                          }
                        }
                        """
                                    ))),
                    @ApiResponse(responseCode = "400", description = "Missing token",
                            content = @Content(examples = @ExampleObject(
                                    name = "TokenRequired",
                                    value = """
                    {
                      "success": false,
                      "code": "TOKEN_REQUIRED",
                      "message": "Join token is required."
                    }
                    """
                            ))),
                    @ApiResponse(responseCode = "403", description = "Password required / access denied",
                            content = @Content(examples = {
                                    @ExampleObject(
                                            name = "PasswordRequired",
                                            value = """
                        {
                          "success": false,
                          "code": "PASSWORD_REQUIRED",
                          "message": "Password incorrect or missing."
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "AccessDenied",
                                            value = """
                        {
                          "success": false,
                          "code": "MEETING_ACCESS_DENIED",
                          "message": "Unauthorized, invalid token, or meeting unavailable"
                        }
                        """
                                    )
                            })),
                    @ApiResponse(responseCode = "410", description = "Meeting completed/expired",
                            content = @Content(examples = {
                                    @ExampleObject(
                                            name = "Completed",
                                            value = """
                        {
                          "success": false,
                          "code": "MEETING_COMPLETED",
                          "message": "This meeting is over."
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Expired",
                                            value = """
                        {
                          "success": false,
                          "code": "MEETING_EXPIRED",
                          "message": "This meeting link has expired."
                        }
                        """
                                    )
                            })),
                    @ApiResponse(responseCode = "500", description = "Server error",
                            content = @Content(examples = @ExampleObject(
                                    name = "InternalError",
                                    value = """
                    { "success": false, "code": "INTERNAL_ERROR", "message": "Unexpected error while fetching open meeting" }
                    """
                            )))
            }
    )
    ResponseEntity<?> openJoin(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String id,
            @Valid @org.springframework.web.bind.annotation.RequestBody OpenJoinRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    );

    // ---------- JOIN (auth user) ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "joinAsUser",
            summary = "Join as authenticated user",
            description = """
            **POST /api/meetings/{id}/join**  
            Host or authorized attendee joins. Host may auto-start scheduled meetings; host receives moderator token.
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Joined / Not started",
                            content = @Content(examples = {
                                    @ExampleObject(
                                            name = "JoinedSuccess",
                                            value = """
                        {
                          "success": true,
                          "code": "MEETING_JOINED_SUCCESS",
                          "message": "You can join now.",
                          "data": {
                            "meeting": { "id": "m_123", "status": "STARTED" },
                            "token": "eyJh...",
                            "room": "room_abc",
                            "exp": "2024-06-23T12:34:56Z"
                          }
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "NotStarted",
                                            value = """
                        {
                          "success": true,
                          "code": "MEETING_NOT_STARTED",
                          "message": "Host hasn’t started yet",
                          "data": { "id": "m_123", "status": "SCHEDULED" }
                        }
                        """
                                    )
                            })),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(examples = @ExampleObject(
                                    name = "AccessDenied",
                                    value = """
                    {
                      "success": false,
                      "code": "MEETING_ACCESS_DENIED",
                      "message": "Unauthorized or not found"
                    }
                    """
                            ))),
                    @ApiResponse(responseCode = "500", description = "Server error",
                            content = @Content(examples = @ExampleObject(
                                    name = "InternalError",
                                    value = """
                    { "success": false, "code": "INTERNAL_ERROR", "message": "Unexpected error while fetching meeting" }
                    """
                            )))
            }
    )
    ResponseEntity<?> joinAsUser(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String id,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String password,
            HttpServletRequest req,
            HttpServletResponse res
    );

    // ---------- GET OPEN (guest) ----------
    @Operation(
            operationId = "getOpenMeeting",
            summary = "Get open meeting",
            description = """
            **GET /api/meetings/open/{id}?token=...&name=...**  
            Validates open link token. When STARTED, mints a non-moderator JWT for the guest and returns join payload.
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Fetched / Joined",
                            content = @Content(examples = {
                                    @ExampleObject(
                                            name = "JoinedSuccess",
                                            value = """
                        {
                          "success": true,
                          "code": "MEETING_JOINED_SUCCESS",
                          "message": "meeting.join.success",
                          "data": {
                            "meeting": { "id": "m_123", "status": "STARTED" },
                            "token": "eyJh...",
                            "room": "room_abc",
                            "exp": "2024-06-23T12:34:56Z"
                          }
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "NotStarted",
                                            value = """
                        {
                          "success": true,
                          "code": "MEETING_NOT_STARTED",
                          "message": "Host hasn’t started the meeting yet.",
                          "data": { "id": "m_123", "status": "SCHEDULED" }
                        }
                        """
                                    )
                            })),
                    @ApiResponse(responseCode = "401", description = "Token required",
                            content = @Content(examples = @ExampleObject(
                                    name = "TokenRequired",
                                    value = """
                    {
                      "success": false,
                      "code": "TOKEN_REQUIRED",
                      "message": "join-token.required"
                    }
                    """
                            ))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(examples = @ExampleObject(
                                    name = "AccessDenied",
                                    value = """
                    {
                      "success": false,
                      "code": "MEETING_ACCESS_DENIED",
                      "message": "Unauthorized, invalid token, or meeting unavailable"
                    }
                    """
                            ))),
                    @ApiResponse(responseCode = "410", description = "Meeting completed/expired",
                            content = @Content(examples = {
                                    @ExampleObject(
                                            name = "Completed",
                                            value = """
                        {
                          "success": false,
                          "code": "MEETING_COMPLETED",
                          "message": "This meeting is over."
                        }
                        """
                                    ),
                                    @ExampleObject(
                                            name = "Expired",
                                            value = """
                        {
                          "success": false,
                          "code": "MEETING_EXPIRED",
                          "message": "This meeting link has expired."
                        }
                        """
                                    )
                            })),
                    @ApiResponse(responseCode = "500", description = "Server error",
                            content = @Content(examples = @ExampleObject(
                                    name = "InternalError",
                                    value = """
                    { "success": false, "code": "INTERNAL_ERROR", "message": "Unexpected error while fetching open meeting" }
                    """
                            )))
            }
    )
    ResponseEntity<?> getOpenMeeting(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String id,
            @Parameter(in = ParameterIn.QUERY, description = "Open link token")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String token,
            @Parameter(in = ParameterIn.QUERY, description = "Optional display name")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String name,
            @RequestParam(required = false) String password,
            HttpServletRequest request,
            HttpServletResponse response
    );

    // ---------- PING ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "ping",
            summary = "Ping",
            description = "**GET /api/meetings/ping** — Health/ping for the current authenticated user."
    )
    ResponseEntity<String> ping();

    // ---------- MY MEETINGS ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "getMyMeetings",
            summary = "Get my meetings",
            description = "**GET /api/meetings/my** — All meetings where the current user is host or attendee.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List",
                            content = @Content(schema = @Schema(implementation = Meeting.class)))
            }
    )
    ResponseEntity<MeetingResponse<List<MeetingDto>>> getMyMeetings();

    // ---------- COMPLETE ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "completeMeeting",
            summary = "Mark meeting completed",
            description = "**PUT /api/meetings/{id}/complete** — Host marks the meeting as COMPLETED.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Completed",
                            content = @Content(examples = @ExampleObject(
                                    name = "CompletedOK",
                                    value = """
                    { "code": "MEETING_COMPLETED", "message": "meeting.update.mark-completed" }
                    """
                            ))),
                    @ApiResponse(responseCode = "403", description = "Not allowed",
                            content = @Content(examples = @ExampleObject(
                                    name = "NotAllowed",
                                    value = """
                    { "code": "MEETING_COMPLETE_FAILED", "message": "meeting.update.not-allowed" }
                    """
                            )))
            }
    )
    ResponseEntity<?> completeMeeting(@Parameter(in = ParameterIn.PATH, description = "Meeting ID") String id);

    // ---------- HOST VIEW ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "getMeetingsHostView",
            summary = "Get meetings (host view)",
            description = "**GET /api/meetings** — All meetings hosted by the current user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List",
                            content = @Content(schema = @Schema(implementation = MeetingDto.class)))
            }
    )
    ResponseEntity<List<MeetingDto>> getMeetings();

    // ---------- APPROVE ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "approveParticipant",
            summary = "Approve participant",
            description = "**PATCH /api/meetings/{meetingId}/approve** — Host approves a participant’s join request.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Approved",
                            content = @Content(examples = @ExampleObject(
                                    name = "Approved",
                                    value = """
                    { "message": "meeting.participant.approved " }
                    """
                            ))),
                    @ApiResponse(responseCode = "403", description = "Not allowed",
                            content = @Content(examples = @ExampleObject(
                                    name = "NotAllowed",
                                    value = """
                    { "error": "meeting.participant.approve.not-allowed" }
                    """
                            )))
            }
    )
    ResponseEntity<?> approveParticipant(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String meetingId,
            @Valid @org.springframework.web.bind.annotation.RequestBody ApproveRejectRequest request
    );

    // ---------- REJECT ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "rejectParticipant",
            summary = "Reject participant",
            description = "**PATCH /api/meetings/{meetingId}/reject** — Host rejects a participant’s join request.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Rejected",
                            content = @Content(examples = @ExampleObject(
                                    name = "Rejected",
                                    value = """
                    { "message": "meeting.participant.reject" }
                    """
                            ))),
                    @ApiResponse(responseCode = "403", description = "Not allowed",
                            content = @Content(examples = @ExampleObject(
                                    name = "NotAllowed",
                                    value = """
                    { "error": "meeting.participant.reject.not-allowed" }
                    """
                            )))
            }
    )
    ResponseEntity<?> rejectParticipant(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String meetingId,
            @Valid @org.springframework.web.bind.annotation.RequestBody ApproveRejectRequest request
    );

    // ---------- EDIT ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "editMeeting",
            summary = "Edit meeting",
            description = "**PUT /api/meetings/{id}** — Host updates meeting details.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Updated",
                            content = @Content(examples = @ExampleObject(
                                    name = "EditOK",
                                    value = """
                    {
                      "success": true,
                      "code": "SUCCESS",
                      "message": "meeting.update.success",
                      "data": { "id": "m_123" }
                    }
                    """
                            ))),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(examples = @ExampleObject(
                                    name = "BadRequest",
                                    value = """
                    { "success": false, "code": "BAD_REQUEST", "message": "Invalid value" }
                    """
                            ))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(examples = @ExampleObject(
                                    name = "AccessDenied",
                                    value = """
                    { "success": false, "code": "MEETING_ACCESS_DENIED", "message": "Not allowed" }
                    """
                            )))
            }
    )
    ResponseEntity<MeetingResponse> editMeeting(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String id,
            @Valid @org.springframework.web.bind.annotation.RequestBody EditMeetingRequest request
    );

    // ---------- DELETE ----------
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "deleteMeeting",
            summary = "Delete meeting",
            description = "**DELETE /api/meetings/{id}** — Host deletes a meeting.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Deleted",
                            content = @Content(examples = @ExampleObject(
                                    name = "DeleteOK",
                                    value = """
                    { "success": true, "code": "SUCCESS", "message": "meeting.delete.success" }
                    """
                            ))),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(examples = @ExampleObject(
                                    name = "BadRequest",
                                    value = """
                    { "success": false, "code": "BAD_REQUEST", "message": "Invalid request" }
                    """
                            ))),
                    @ApiResponse(responseCode = "403", description = "Access denied",
                            content = @Content(examples = @ExampleObject(
                                    name = "AccessDenied",
                                    value = """
                    { "success": false, "code": "MEETING_ACCESS_DENIED", "message": "Not allowed" }
                    """
                            )))
            }
    )
    ResponseEntity<MeetingResponse> deleteMeeting(
            @Parameter(in = ParameterIn.PATH, description = "Meeting ID") String id
    );
}
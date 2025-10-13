package com.algomeet.meetservice.controller;

import com.algomeet.meetservice.Dto.*;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.controller.swagger.MeetingControllerDoc;
import com.algomeet.meetservice.mapper.MeetingMapper;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.MeetingStatus;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.security.AlgomeetMeetingTokenRegistry;
import com.algomeet.meetservice.security.GuestIdentity;
import com.algomeet.meetservice.service.AlgomeetJwtService;
import com.algomeet.meetservice.service.MeetingService;
import static com.algomeet.meetservice.util.MessageUtil.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController implements MeetingControllerDoc {

    private static final Logger log = LoggerFactory.getLogger(MeetingController.class);

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private UserDirectoryClient userDirectoryClient;

    private final AlgomeetJwtService algomeetJwtService;
    private final AlgomeetMeetingTokenRegistry tokenRegistry;

    private final  MeetingMapper mapper;

    @PostMapping(value = "/create")
    public ResponseEntity<MeetingDto> createMeeting(@RequestBody MeetingRequest request) {
        var created = meetingService.createMeeting(currentUser(), request);
        return ResponseEntity.ok( mapper.toDto(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse<MeetingDto>> getMeeting(
            @PathVariable String id,
            @RequestParam(required = false) String token) {

        String email = currentUser();
        log.info("GetMeeting request: id={}, by user={}", id, maskEmail(email));
        if (token != null)
            log.debug("Join token provided (length={})", token.length());

        try {
            var meetingOpt = meetingService.getMeetingById(id, email, token);
            if (meetingOpt.isEmpty()) {
                log.warn("GetMeeting denied: id={}, user={}", id, maskEmail(email));
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(MeetingResponse.error("MEETING_ACCESS_DENIED",
                                "Unauthorized, invalid token, or meeting unavailable"));
            }

            var meeting = meetingOpt.get();
            String code, msg;
            switch (meeting.getStatus()) {
                case STARTED -> {
                    code = "MEETING_JOINED_SUCCESS";
                    msg = "You can join now.";
                }
                case SCHEDULED -> {
                    code = "MEETING_NOT_STARTED";
                    msg = "Host hasn’t started the meeting yet.";
                }
                case COMPLETED -> {
                    code = "MEETING_COMPLETED";
                    msg = "This meeting is over.";
                }
                case EXPIRED -> {
                    code = "MEETING_EXPIRED";
                    msg = "This meeting link has expired.";
                }
                default -> {
                    code = "MEETING_FETCH_SUCCESS";
                    msg = "Meeting fetched.";
                }
            }

            var dto =  mapper.toDto(meeting);
            log.info("GetMeeting success: id={}, status={}", id, meeting.getStatus());
            return ResponseEntity.ok(MeetingResponse.success(code, msg, dto));

        } catch (AccessDeniedException e) {
            log.warn("GetMeeting access denied: id={}, user={}, reason={}", id, maskEmail(email), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", e.getMessage()));
        } catch (Exception e) {
            log.error("GetMeeting error: id={}, user={}, ex={}", id, maskEmail(email), e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error("INTERNAL_ERROR", "Unexpected error while fetching meeting"));
        }
    }

    /** Unified open join endpoint: validates password for non-hosts, mints JWT. */
    @PostMapping("/open/{id}/join")
    public ResponseEntity<?> openJoin(
            @PathVariable String id,
            @RequestBody OpenJoinRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (req == null || req.token() == null || req.token().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(MeetingResponse.error("TOKEN_REQUIRED", "Join token is required."));
        }
        final String guestKey = GuestIdentity.resolve(request, response);

        return meetingService.getOpenMeetingById(id, req.token().trim())
                .map(m -> {
                    if (m.getStatus() == MeetingStatus.COMPLETED) {
                        return ResponseEntity.status(HttpStatus.GONE)
                                .body(MeetingResponse.error("MEETING_COMPLETED", "This meeting is over."));
                    }
                    if (m.getStatus() == MeetingStatus.EXPIRED) {
                        return ResponseEntity.status(HttpStatus.GONE)
                                .body(MeetingResponse.error("MEETING_EXPIRED", "This meeting link has expired."));
                    }
                    if (!meetingService.verifyPassword(m, req.password())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(MeetingResponse.error("PASSWORD_REQUIRED", "Password incorrect or missing."));
                    }

                    var existing = tokenRegistry.getIfActive(m.getId(), guestKey);
                    existing.ifPresent(t -> tokenRegistry.revoke(m.getId(), guestKey));

                    var gen = algomeetJwtService.generateForMeeting(
                            m, guestKey, (req.name() == null ? "" : req.name().trim()), null, false);

                    tokenRegistry.save(m.getId(), guestKey, gen.token(), Duration.ofSeconds(300));

                    var dto =  mapper.toDto(m);
                    return ResponseEntity.ok(MeetingResponse.success(
                            "MEETING_JOINED_SUCCESS", "You can join now.",
                            new OpenMeetingJoinResponse(dto, gen.token(), gen.room(), gen.exp())
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(MeetingResponse.error("MEETING_ACCESS_DENIED",
                                "Unauthorized, invalid token, or meeting unavailable")));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinAsUser(
            @PathVariable String id,
            HttpServletRequest req,
            HttpServletResponse res
    ) {
        final String email = currentUser(); // from SecurityContext

        var mOpt = meetingService.getMeetingById(id, email, /*token*/ null);
        if (mOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", "Unauthorized or not found"));
        }

        var m = mOpt.get();
        boolean isHost = email.equalsIgnoreCase(m.getHostEmail());

        // Start on first host join
        if (isHost && m.getStatus() == MeetingStatus.SCHEDULED) {
            meetingService.startIfScheduledByHost(m);
        }

        // If not started yet and user is not the host, don’t mint a token
        if (m.getStatus() != MeetingStatus.STARTED && !isHost) {
            return ResponseEntity.ok(MeetingResponse.success(
                    "MEETING_NOT_STARTED", "Host hasn’t started yet", mapper.toDto(m)));
        }

        // Identify the logged-in user
        String userId;
        String display;
        try {
            var ud = userDirectoryClient.exact(email);
            userId = ud.userKey() != null ? ud.userKey().toString() : email;
            display = ud.displayName() != null ? ud.displayName() : email;
        } catch (Exception e) {
            // Safe fallback if directory is down or not provisioned
            userId = email;
            display = email;
        }

        // Reuse existing active token if present
        var existingOpt = tokenRegistry.getIfActive(m.getId(), userId);
        if (existingOpt.isPresent()) {
            var reused = new AlgomeetJwtService.GeneratedAlgomeetToken(
                    existingOpt.get(),
                    m.getRoom() != null ? m.getRoom().getRoomId() : null,
                    java.time.Instant.now().plusSeconds(300), // best-effort exp window
                    "reused"
            );
            return ResponseEntity.ok(MeetingResponse.success(
                    "MEETING_JOINED_SUCCESS", "You can join now.",
                    new OpenMeetingJoinResponse(mapper.toDto(m), reused.token(), reused.room(), reused.exp())
            ));
        }

        // Mint fresh JWT (host becomes moderator)
        var gen = algomeetJwtService.generateForMeeting(
                m, userId, display, /*avatar*/ null, /*moderator*/ isHost);

        tokenRegistry.save(m.getId(), userId, gen.token(), java.time.Duration.ofMinutes(5));

        return ResponseEntity.ok(MeetingResponse.success(
                "MEETING_JOINED_SUCCESS", "You can join now.",
                new OpenMeetingJoinResponse(mapper.toDto(m), gen.token(), gen.room(), gen.exp())
        ));
    }


    @GetMapping("/open/{id}")
    public ResponseEntity<?> getOpenMeeting(
            @PathVariable String id,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String name,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String userName = SecurityContextHolder.getContext().getAuthentication().getName();

        if (token == null || token.isBlank()) {
            log.warn("OpenMeeting missing token: id={}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MeetingResponse.error("TOKEN_REQUIRED", i18n("join-token.required")));

        }

        try {
            final String linkToken = token.trim();
            final String guestKey = GuestIdentity.resolve(request, response);

            return meetingService.getOpenMeetingById(id, linkToken)
                    .map(m -> switch (m.getStatus()) {
                        case STARTED -> {
                            var existing = tokenRegistry.getIfActive(m.getId(), guestKey);
                            existing.ifPresent(t -> tokenRegistry.revoke(m.getId(), guestKey));

                            String displayName = (userName != null && !userName.isBlank())
                                    ? userName.trim()
                                    : "";

                            boolean moderator = false;

                            var gen = algomeetJwtService.generateForMeeting(
                                    m, guestKey, displayName, null, moderator);

                            tokenRegistry.save(m.getId(), guestKey, gen.token(), java.time.Duration.ofSeconds(300));

                            var dto =  mapper.toDto(m);
                            yield ResponseEntity.ok(MeetingResponse.success(
                                    "MEETING_JOINED_SUCCESS", "meeting.join.success",
                                    new OpenMeetingJoinResponse(dto, gen.token(), gen.room(), gen.exp())
                            ));
                        }
                        case SCHEDULED -> ResponseEntity.ok(
                                MeetingResponse.success("MEETING_NOT_STARTED", "Host hasn’t started the meeting yet.", mapper.toDto(m)));

                        case COMPLETED -> ResponseEntity.status(HttpStatus.GONE)
                                .body(MeetingResponse.error("MEETING_COMPLETED", "This meeting is over."));
                        case EXPIRED -> ResponseEntity.status(HttpStatus.GONE)
                                .body(MeetingResponse.error("MEETING_EXPIRED", "This meeting link has expired."));

                        default -> ResponseEntity.ok(
                                MeetingResponse.success("MEETING_FETCH_SUCCESS", "Meeting fetched.", mapper.toDto(m)));
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(MeetingResponse.error("MEETING_ACCESS_DENIED",
                                    "Unauthorized, invalid token, or meeting unavailable")));

        } catch (AccessDeniedException e) {
            log.warn("OpenMeeting access denied: id={}, reason={}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", e.getMessage()));
        } catch (Exception e) {
            log.error("OpenMeeting error: id={}, ex={}", id, e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error("INTERNAL_ERROR", "Unexpected error while fetching open meeting"));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        String user = currentUser();
        log.debug("Ping by user={}", maskEmail(user));
        return ResponseEntity.ok("Hello " + user);
    }

    public List<Meeting> getMeetingsByHostEmail(String hostEmail) {
        log.debug("getMeetingsByHostEmail: host={}", maskEmail(hostEmail));
        return meetingService.getMeetingsByHostEmail(hostEmail);
    }

    // Get All Meetings for User (host or attendee)
    @GetMapping("/my")
    public ResponseEntity<List<Meeting>> getMyMeetings() {
        String email = currentUser();
        log.info("GetMyMeetings request by user={}", maskEmail(email));
        List<Meeting> meetings = meetingService.getMeetingsForUser(email);
        log.info("GetMyMeetings: user={}, count={}", maskEmail(email), meetings.size());
        return ResponseEntity.ok(meetings);
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeMeeting(@PathVariable String id) {
        String email = currentUser();
        log.info("CompleteMeeting request: id={}, by={}", id, maskEmail(email));
        boolean updated = meetingService.markMeetingAsCompleted(id, email);

        if (updated) {
            log.info("CompleteMeeting success: id={}", id);
            // TODO: Integrate notification-service for push notifications
            return ResponseEntity.ok(Map.of(
                    "code", "MEETING_COMPLETED",
                    "message", i18n("meeting.update.mark-completed")
            ));
        } else {
            log.warn("CompleteMeeting failed/forbidden: id={}, by={}", id, maskEmail(email));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "MEETING_COMPLETE_FAILED",
                    "message", i18n("meeting.update.not-allowed")
            ));
        }
    }

    @GetMapping
    public ResponseEntity<List<MeetingDto>> getMeetings() {
        String email = currentUser();
        log.info("GetMeetings (host view) request by user={}", maskEmail(email));
        var list = meetingService.getMeetingsByHostEmail(email)
                .stream().map( mapper::toDto).toList();
        log.info("GetMeetings: host={}, count={}", maskEmail(email), list.size());
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{meetingId}/approve")
    public ResponseEntity<?> approveParticipant(
            @PathVariable String meetingId,
            @RequestBody ApproveRejectRequest request) {

        String hostEmail = currentUser();
        log.info("ApproveParticipant: meetingId={}, host={}, attendee={}",
                meetingId, maskEmail(hostEmail), maskEmail(request.getAttendeeEmail()));
        boolean success = meetingService.approveParticipant(meetingId, hostEmail, request.getAttendeeEmail());

        if (success) {
            log.info("ApproveParticipant success: meetingId={}, attendee={}", meetingId, maskEmail(request.getAttendeeEmail()));
            return ResponseEntity.ok(Map.of("message", i18n("meeting.participant.approved ")));
        } else {
            log.warn("ApproveParticipant denied/invalid: meetingId={}, host={}", meetingId, maskEmail(hostEmail));
            return ResponseEntity.status(403).body(Map.of("error", i18n("meeting.participant.approve.not-allowed")));
        }
    }

    @PatchMapping("/{meetingId}/reject")
    public ResponseEntity<?> rejectParticipant(
            @PathVariable String meetingId,
            @RequestBody ApproveRejectRequest request) {

        String hostEmail = currentUser();
        log.info("RejectParticipant: meetingId={}, host={}, attendee={}",
                meetingId, maskEmail(hostEmail), maskEmail(request.getAttendeeEmail()));
        boolean success = meetingService.rejectParticipant(meetingId, hostEmail, request.getAttendeeEmail());

        if (success) {
            log.info("RejectParticipant success: meetingId={}, attendee={}", meetingId, maskEmail(request.getAttendeeEmail()));
            return ResponseEntity.ok(Map.of("message", i18n("meeting.participant.reject")));
        } else {
            log.warn("RejectParticipant denied/invalid: meetingId={}, host={}", meetingId, maskEmail(hostEmail));
            return ResponseEntity.status(403).body(Map.of("error", i18n("meeting.participant.reject.not-allowed")));
        }
    }

    // TODO: Move exception handling to a global @RestControllerAdvice later.
    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> editMeeting(
            @PathVariable String id,
            @RequestBody EditMeetingRequest request) {

        String email = currentUser();
        log.info("EditMeeting request: id={}, by={}", id, maskEmail(email));
        log.info("EditMeeting request: id={}, by={}", id, maskEmail(email));
        try {
            var updated = meetingService.updateMeeting(email, id, request);
            var dto =  mapper.toDto(updated);
            log.info("EditMeeting success: id={}", id);
            return ResponseEntity.ok(MeetingResponse.success("SUCCESS", i18n("meeting.update.success"), updated));
        } catch (AccessDeniedException ade) {
            log.warn("EditMeeting access denied: id={}, by={}, reason={}", id, maskEmail(email), ade.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", ade.getMessage()));
        } catch (IllegalArgumentException iae) {
            log.warn("EditMeeting bad request: id={}, reason={}", id, iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MeetingResponse.error("BAD_REQUEST", iae.getMessage()));
        } catch (Exception e) {
            log.error("EditMeeting error: id={}, ex={}", id, e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error("INTERNAL_ERROR", i18n("meeting.update.unexpected-error")));
        }
    }

    // TODO: Move exception handling to a global @RestControllerAdvice later.
    @DeleteMapping("/{id}")
    public ResponseEntity<MeetingResponse> deleteMeeting(@PathVariable String id) {
        String email = currentUser();
        log.info("DeleteMeeting request: id={}, by={}", id, maskEmail(email));
        try {
            meetingService.deleteMeeting(email, id);
            log.info("DeleteMeeting success: id={}", id);
            return ResponseEntity.ok(MeetingResponse.success("SUCCESS", i18n("meeting.delete.success"), null));
        } catch (AccessDeniedException ade) {
            log.warn("DeleteMeeting access denied: id={}, by={}, reason={}", id, maskEmail(email), ade.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", ade.getMessage()));
        } catch (IllegalArgumentException iae) {
            log.warn("DeleteMeeting bad request: id={}, reason={}", id, iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MeetingResponse.error("BAD_REQUEST", iae.getMessage()));
        } catch (Exception e) {
            log.error("DeleteMeeting error: id={}, ex={}", id, e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error("INTERNAL_ERROR", "Unexpected error while deleting meeting"));
        }
    }

    /* ---------- helpers ---------- */

    private String currentUser() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // Avoid logging full PII or secrets
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "unknown";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return maskedLocal + "@" + domain;
    }
}

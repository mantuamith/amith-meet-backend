package com.algomeet.meetservice.controller;

import com.algomeet.meetservice.Dto.EditMeetingRequest;
import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.Dto.MeetingResponse;
import com.algomeet.meetservice.Dto.ApproveRejectRequest;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.service.MeetingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private static final Logger log = LoggerFactory.getLogger(MeetingController.class);

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @PostMapping("/create")
    public ResponseEntity<Meeting> createMeeting(@RequestBody MeetingRequest request) {
        String email = currentUser();
        log.info("CreateMeeting request by user={}", maskEmail(email));
        log.debug("CreateMeeting payload received");
        Meeting created = meetingService.createMeeting(email, request);
        log.info("CreateMeeting success: id={}, host={}", created.getId(), maskEmail(created.getHostEmail()));
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(
            @PathVariable String id,
            @RequestParam(required = false) String token) {

        String email = currentUser();
        log.info("GetMeeting request: id={}, by user={}", id, maskEmail(email));
        if (token != null) {
            log.debug("Join token provided (length={})", token.length());
        }

        try {
            Optional<Meeting> meetingOpt = meetingService.getMeetingById(id, email, token);
            if (meetingOpt.isEmpty()) {
                log.warn("GetMeeting denied: id={}, user={}", id, maskEmail(email));
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(MeetingResponse.error("MEETING_ACCESS_DENIED",
                                "Unauthorized, invalid token, or meeting unavailable"));
            }

            Meeting meeting = meetingOpt.get();
            String code;
            String msg;
            switch (meeting.getStatus()) {
                case STARTED -> { code = "MEETING_JOINED_SUCCESS"; msg = "You can join now."; }
                case SCHEDULED -> { code = "MEETING_NOT_STARTED";  msg = "Host hasn’t started the meeting yet."; }
                case COMPLETED -> { code = "MEETING_COMPLETED";    msg = "This meeting is over."; }
                case EXPIRED -> { code = "MEETING_EXPIRED";        msg = "This meeting link has expired."; }
                default -> { code = "MEETING_FETCH_SUCCESS";       msg = "Meeting fetched."; }
            }
            log.info("GetMeeting success: id={}, status={}", id, meeting.getStatus());
            return ResponseEntity.ok(MeetingResponse.success(code, msg, meeting));

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

    // For open meetings (guests). Never log token value.
    @GetMapping("/open/{id}")
    public ResponseEntity<?> getOpenMeeting(
            @PathVariable String id,
            @RequestParam(required = false) String token) {

        if (token == null || token.isBlank()) {
            log.warn("OpenMeeting missing token: id={}", id);
            return ResponseEntity.badRequest()
                    .body(MeetingResponse.error("TOKEN_REQUIRED", "Join token is required."));
        }

        try {
            log.info("OpenMeeting request: id={}, tokenLen={}", id, token.length());
            return meetingService.getOpenMeetingById(id, token.trim())
                    .map(m -> {
                        log.debug("OpenMeeting found: id={}, status={}", id, m.getStatus());
                        return switch (m.getStatus()) {
                            case STARTED -> ResponseEntity.ok(
                                    MeetingResponse.<Meeting>success("MEETING_JOINED_SUCCESS", "You can join now.", m));
                            case SCHEDULED -> ResponseEntity.ok(
                                    MeetingResponse.<Meeting>success("MEETING_NOT_STARTED", "Host hasn’t started the meeting yet.", m));
                            case COMPLETED -> {
                                log.info("OpenMeeting completed: id={}", id);
                                yield ResponseEntity.status(HttpStatus.GONE)
                                        .body(MeetingResponse.error("MEETING_COMPLETED", "This meeting is over."));
                            }
                            case EXPIRED -> {
                                log.info("OpenMeeting expired: id={}", id);
                                yield ResponseEntity.status(HttpStatus.GONE)
                                        .body(MeetingResponse.error("MEETING_EXPIRED", "This meeting link has expired."));
                            }
                            default -> ResponseEntity.ok(
                                    MeetingResponse.<Meeting>success("MEETING_FETCH_SUCCESS", "Meeting fetched.", m));
                        };
                    })
                    .orElseGet(() -> {
                        log.warn("OpenMeeting access denied/unavailable: id={}", id);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(MeetingResponse.error("MEETING_ACCESS_DENIED",
                                        "Unauthorized, invalid token, or meeting unavailable"));
                    });
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
                    "message", "Meeting marked as completed"
            ));
        } else {
            log.warn("CompleteMeeting failed/forbidden: id={}, by={}", id, maskEmail(email));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "MEETING_COMPLETE_FAILED",
                    "message", "Not allowed or meeting not found"
            ));
        }
    }

    @GetMapping
    public ResponseEntity<List<Meeting>> getMeetings() {
        String email = currentUser();
        log.info("GetMeetings (host view) request by user={}", maskEmail(email));
        List<Meeting> list = meetingService.getMeetingsByHostEmail(email);
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
            return ResponseEntity.ok(Map.of("message", "Participant approved"));
        } else {
            log.warn("ApproveParticipant denied/invalid: meetingId={}, host={}", meetingId, maskEmail(hostEmail));
            return ResponseEntity.status(403).body(Map.of("error", "Not allowed or invalid"));
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
            return ResponseEntity.ok(Map.of("message", "Participant rejected"));
        } else {
            log.warn("RejectParticipant denied/invalid: meetingId={}, host={}", meetingId, maskEmail(hostEmail));
            return ResponseEntity.status(403).body(Map.of("error", "Not allowed or invalid"));
        }
    }

    // TODO: Move exception handling to a global @RestControllerAdvice later.
    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> editMeeting(
            @PathVariable String id,
            @RequestBody EditMeetingRequest request) {

        String email = currentUser();
        log.info("EditMeeting request: id={}, by={}", id, maskEmail(email));
        try {
            Meeting updated = meetingService.updateMeeting(email, id, request);
            log.info("EditMeeting success: id={}", id);
            return ResponseEntity.ok(MeetingResponse.success("SUCCESS", "Meeting updated", updated));
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
                    .body(MeetingResponse.error("INTERNAL_ERROR", "Unexpected error while updating meeting"));
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
            return ResponseEntity.ok(MeetingResponse.success("SUCCESS", "Meeting deleted", null));
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

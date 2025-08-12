package com.algomeet.meetservice.controller;

import com.algomeet.meetservice.Dto.EditMeetingRequest;
import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.Dto.MeetingResponse;
import com.algomeet.meetservice.Dto.ApproveRejectRequest;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.service.MeetingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;


import java.util.List;
import java.util.Map;
import java.util.Optional;

/***
 * Step 7 — Notification-Service Changes
 * Add device token storage for users.
 *
 * Implement push notifications via:
 *
 * APNs → iOS/macOS
 *
 * FCM → Android/Web
 *
 *
 */

/**
 * /getMeetings between range of Dates
 * /editMeetings -
 * /Expired/Completed Meetings
 * /camcelmeeting
 * /ICS File
 *
 */

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    @Autowired
    private MeetingService meetingService;
    @Autowired
    private MeetingRepository meetingRepository;

    @PostMapping("/create")
    public ResponseEntity<Meeting> createMeeting(@RequestBody MeetingRequest request) {
        System.out.println("[DEBUG] >>> CreateMeeting endpoint hit");
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.createMeeting(email, request));
    }


    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(
            @PathVariable String id,
            @RequestParam(required = false) String token) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            Optional<Meeting> meeting = meetingService.getMeetingById(id, email, token);
            if (meeting.isPresent()) {
                return ResponseEntity.ok(MeetingResponse.success("SUCCESS","Meeting fetch Successful",meeting.get()));
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(MeetingResponse.error("MEETING_ACCESS_DENIED", "Unauthorized or invalid token"));
            }
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", e.getMessage()));
        }
    }



    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok("Hello " + user);
    }


    public List<Meeting> getMeetingsByHostEmail(String hostEmail) {
        return meetingService.getMeetingsByHostEmail(hostEmail);
    }

    // Get All Meetings for User (host or attendee)
    @GetMapping("/my")
    public ResponseEntity<List<Meeting>> getMyMeetings() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.getMeetingsForUser(email));
    }


    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeMeeting(@PathVariable String id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean updated = meetingService.markMeetingAsCompleted(id, email);

        if (updated) {
            // TODO: Integrate with notification-service to send push notification to all attendees
            // Example:
            // notificationService.sendMeetingEndedNotification(meetingId, attendees);
            return ResponseEntity.ok(Map.of(
                    "code", "MEETING_COMPLETED",
                    "message", "Meeting marked as completed"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "MEETING_COMPLETE_FAILED",
                    "message", "Not allowed or meeting not found"
            ));
        }
    }

    @GetMapping
    public ResponseEntity<List<Meeting>> getMeetings() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.getMeetingsByHostEmail(email));
    }

    @PatchMapping("/{meetingId}/approve")
    public ResponseEntity<?> approveParticipant(
            @PathVariable String meetingId,
            @RequestBody ApproveRejectRequest request) {

        String hostEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean success = meetingService.approveParticipant(meetingId, hostEmail, request.getAttendeeEmail());

        return success
                ? ResponseEntity.ok(Map.of("message", "Participant approved"))
                : ResponseEntity.status(403).body(Map.of("error", "Not allowed or invalid"));
    }

    @PatchMapping("/{meetingId}/reject")
    public ResponseEntity<?> rejectParticipant(
            @PathVariable String meetingId,
            @RequestBody ApproveRejectRequest request) {

        String hostEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean success = meetingService.rejectParticipant(meetingId, hostEmail, request.getAttendeeEmail());

        return success
                ? ResponseEntity.ok(Map.of("message", "Participant rejected"))
                : ResponseEntity.status(403).body(Map.of("error", "Not allowed or invalid"));
    }

    // TODO: Move exception handling to a global @RestControllerAdvice later.
    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> editMeeting(
            @PathVariable String id,
            @RequestBody EditMeetingRequest request) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            Meeting updated = meetingService.updateMeeting(email, id, request);
            return ResponseEntity.ok(
                    MeetingResponse.success("SUCCESS", "Meeting updated", updated)
            );
        } catch (AccessDeniedException ade) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", ade.getMessage()));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MeetingResponse.error("BAD_REQUEST", iae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error("INTERNAL_ERROR", "Unexpected error while updating meeting"));
        }
    }

    // TODO: Move exception handling to a global @RestControllerAdvice later.
    @DeleteMapping("/{id}")
    public ResponseEntity<MeetingResponse> deleteMeeting(@PathVariable String id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            meetingService.deleteMeeting(email, id);
            return ResponseEntity.ok(
                    MeetingResponse.success("SUCCESS", "Meeting deleted", null)
            );
        } catch (AccessDeniedException ade) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error("MEETING_ACCESS_DENIED", ade.getMessage()));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MeetingResponse.error("BAD_REQUEST", iae.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error("INTERNAL_ERROR", "Unexpected error while deleting meeting"));
        }
    }

}

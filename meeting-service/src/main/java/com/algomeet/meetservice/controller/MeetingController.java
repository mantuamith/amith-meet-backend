package com.algomeet.meetservice.controller;

import com.algomeet.meetservice.Dto.*;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.controller.swagger.MeetingControllerDoc;
import com.algomeet.meetservice.mapper.MeetingMapper;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.MeetingStatus;
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
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController implements MeetingControllerDoc  {

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
                        .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED,
                                i18n("meeting.access.denied")));
            }

            var meeting = meetingOpt.get();
            String code, msg;
            switch (meeting.getStatus()) {
                case STARTED -> {
                    code = ResponseCodes.MEETING_JOINED_SUCCESS;
                    msg = "You can join now.";
                }
                case SCHEDULED -> {
                    code = ResponseCodes.MEETING_NOT_STARTED;
                    msg = "Host hasn’t started the meeting yet.";
                }
                case COMPLETED -> {
                    code = ResponseCodes.MEETING_COMPLETED;
                    msg = i18n("meeting.completed");
                }
                case EXPIRED -> {
                    code = "MEETING_EXPIRED";
                    msg =  i18n("meeting.expired");
                }
                default -> {
                    code = ResponseCodes.MEETING_FETCH_SUCCESS;
                    msg = i18n("meeting.fetch.success");
                }
            }

            var dto =  mapper.toDto(meeting);
            log.info("GetMeeting success: id={}, status={}", id, meeting.getStatus());
            return ResponseEntity.ok(MeetingResponse.success(code, msg, dto));

        } catch (AccessDeniedException e) {
            log.warn("GetMeeting access denied: id={}, user={}, reason={}", id, maskEmail(email), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED, i18n("meeting_access_denied")));
        } catch (Exception e) {
            log.error("GetMeeting error: id={}, user={}, ex={}", id, maskEmail(email), e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error(ResponseCodes.INTERNAL_ERROR, i18n("meeting_internal_error")));
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

//        final String guestKey = GuestIdentity.resolve(request, response);
        final String guestKey =
                (req != null && req.userKey() != null && !req.userKey().isBlank())
                        ? req.userKey()
                        : GuestIdentity.resolve(request, response);

        Optional<Meeting> meetingOpt = meetingService.getOpenMeetingById(id, null);

        if (meetingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(MeetingResponse.error(
                            ResponseCodes.MEETING_NOT_FOUND,
                            "meeting.not-found"
                    ));
        }

        Meeting m = meetingOpt.get();

        /* -------------------------------------------------
         * 1️⃣ HARD STOP — Lifecycle
         * ------------------------------------------------- */
        if (m.getStatus() == MeetingStatus.EXPIRED) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(MeetingResponse.error(
                            ResponseCodes.MEETING_EXPIRED,
                            i18n("meeting.expired")
                    ));
        }

        if (m.getStatus() == MeetingStatus.COMPLETED) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(MeetingResponse.error(
                            ResponseCodes.MEETING_COMPLETED,
                            i18n("meeting.completed")
                    ));
        }

        /* -------------------------------------------------
         * 2️⃣ MODERATOR ACCESS (Bypass)
         * ------------------------------------------------- */
        boolean isModerator = false;

        if (req.moderatorPassword() != null && !req.moderatorPassword().isBlank()) {

            if (!req.moderatorPassword().trim().equals(m.getModeratorPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(MeetingResponse.error(
                                ResponseCodes.MEETING_ACCESS_DENIED,
                                i18n("meeting.moderator.password.incorrect")
                        ));
            }

            isModerator = true;
        }

        /* -------------------------------------------------
         * 3️⃣ HOST NOT STARTED CHECK (Skip for moderator)
         * ------------------------------------------------- */
        if (!isModerator &&
                m.getStatus() != MeetingStatus.STARTED &&
                m.getHostEmail() != null &&
                !m.getHostEmail().isBlank()) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error(
                            ResponseCodes.MEETING_NOT_STARTED,
                            "Host hasn’t started yet"
                    ));
        }

        /* -------------------------------------------------
         * 4️⃣ ACCESS CONTROL (Hybrid Mode)
         *
         * If passwordEnabled = true
         *   → Allow valid password OR valid token
         *
         * If passwordEnabled = false
         *   → Token mandatory
         * ------------------------------------------------- */
        if (!isModerator) {

            boolean tokenProvided = req.token() != null && !req.token().isBlank();
            boolean passwordProvided = req.password() != null && !req.password().isBlank();

            if (m.isPasswordEnabled()) {

                // 🔴 At least one credential must be provided
                if (!tokenProvided && !passwordProvided) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(MeetingResponse.error(
                                    ResponseCodes.PASSWORD_REQUIRED,
                                    i18n("meeting.password.required")
                            ));
                }

                boolean valid = false;

                // Check password if provided
                if (passwordProvided) {
                    valid = meetingService.verifyPassword(m, req.password());
                }

                // If password not valid, try token (if provided)
                if (!valid && tokenProvided) {
                    valid = req.token().trim().equals(m.getToken());
                }

                if (!valid) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(MeetingResponse.error(
                                    ResponseCodes.MEETING_ACCESS_DENIED,
                                    i18n("meeting.access.denied")
                            ));
                }

            } else {

                // 🔴 Token mandatory when password disabled
                if (!tokenProvided) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(MeetingResponse.error(
                                    ResponseCodes.TOKEN_REQUIRED,
                                    i18n("meeting.token.required")
                            ));
                }

                if (!req.token().trim().equals(m.getToken())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(MeetingResponse.error(
                                    ResponseCodes.MEETING_ACCESS_DENIED,
                                    i18n("meeting.access.denied")
                            ));
                }

                // Password ignored completely
            }
        }

        /* -------------------------------------------------
         * 5️⃣ Revoke existing session
         * ------------------------------------------------- */
        tokenRegistry.getIfActive(m.getId(), guestKey)
                .ifPresent(t -> tokenRegistry.revoke(m.getId(), guestKey));

        /* -------------------------------------------------
         * 6️⃣ Generate JWT
         * ------------------------------------------------- */
        AlgomeetJwtService.GeneratedAlgomeetToken gen =
                algomeetJwtService.generateForMeeting(
                        m,
                        guestKey,
                        (req.name() == null ? "" : req.name().trim()),
                        null,
                        isModerator
                );

        tokenRegistry.save(
                m.getId(),
                guestKey,
                gen.token(),
                Duration.ofSeconds(300)
        );

        var dto = mapper.toDto(m);

        return ResponseEntity.ok(
                MeetingResponse.success(
                        ResponseCodes.MEETING_JOINED_SUCCESS,
                        "You can join now.",
                        new OpenMeetingJoinResponse(
                                dto,
                                gen.token(),
                                gen.room(),
                                gen.exp()
                        )
                )
        );
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinAsUser(
            @PathVariable String id,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String password,
            HttpServletRequest req,
            HttpServletResponse res
    ) {
        final String email = currentUser(); // from SecurityContext

        var mOpt = meetingService.getMeetingById(id, email, token);
        var m = mOpt.get();
        boolean isHost = email.equalsIgnoreCase(m.getHostEmail());
        boolean isAttendee = m.getAttendees() != null && m.getAttendees().stream()
                .anyMatch(a -> a.equalsIgnoreCase(email));

        if ((token == null || token.isBlank()) && (password == null || password.isBlank())) {
            return ResponseEntity.ok(MeetingResponse.success(
                    ResponseCodes.PASSWORD_REQUIRED,
                    i18n("meeting.password.required"),
                    mapper.toDto(m)
            ));
        }

        // Enforce meeting password for logged-in users who are NOT host and NOT in attendees
        if (!isHost && token != null && !m.getToken().equals(token.trim())) {
            return ResponseEntity.ok(MeetingResponse.success(
                    ResponseCodes.MEETING_ACCESS_DENIED,
                    i18n("meeting.access.denied"),
                    mapper.toDto(m)
            ));
        }else if (!isHost && password != null && password.isBlank() ) {
            // No password provided -> prompt frontend to ask for passcode

                return ResponseEntity.ok(MeetingResponse.success(
                        ResponseCodes.PASSWORD_REQUIRED,
                        i18n("meeting.password.required"),
                        mapper.toDto(m)
                ));

            // password correct -> proceed
        }
        if (!isHost && password != null && !password.isBlank() ){
            if (!meetingService.verifyPassword(m, password)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(MeetingResponse.error(ResponseCodes.PASSWORD_INCORRECT, i18n("meeting.password.incorrect")));
            }
        }


            // Start on first host join
        if (isHost && m.getStatus() == MeetingStatus.SCHEDULED) {
            meetingService.startIfScheduledByHost(m);
        }

        // If not started yet and user is not the host, don’t mint a token
        if (m.getStatus() != MeetingStatus.STARTED && !isHost) {
            return ResponseEntity.ok(MeetingResponse.success(
                    ResponseCodes.MEETING_NOT_STARTED, "Host hasn’t started yet", mapper.toDto(m)));
        }

        // Identify the logged-in user
        String userId;
        String display;
        try {
            var ud = userDirectoryClient.exact(email);
            userId = ud.userKey() != null ? ud.userKey().toString() : email;
            display = ud.username() != null ? ud.username() : email;
            log.info("User directory lookup for {} returned userId={}, display={}", email, userId, display);
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
                    ResponseCodes.MEETING_JOINED_SUCCESS, "You can join now.",
                    new OpenMeetingJoinResponse(mapper.toDto(m), reused.token(), reused.room(), reused.exp())
            ));
        }

        // Mint fresh JWT (host becomes moderator)
        var gen = algomeetJwtService.generateForMeeting(
                m, userId, display, /*avatar*/ null, /*moderator*/ isHost);

        tokenRegistry.save(m.getId(), userId, gen.token(), java.time.Duration.ofMinutes(5));

        return ResponseEntity.ok(MeetingResponse.success(
                ResponseCodes.MEETING_JOINED_SUCCESS, "You can join now.",
                new OpenMeetingJoinResponse(mapper.toDto(m), gen.token(), gen.room(), gen.exp())
        ));
    }


    @GetMapping("/open/{id}")
    public ResponseEntity<?> getOpenMeeting(
            @PathVariable String id,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String password,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody OpenJoinRequest req
    ) {
        final String guestKey = GuestIdentity.resolve(request, response);

        return (ResponseEntity<?>) meetingService.getOpenMeetingById(id, req.token())
                .map(m -> {
                    if (m.getStatus() == MeetingStatus.COMPLETED) {
                        return ResponseEntity.status(HttpStatus.GONE)
                                .body(MeetingResponse.error(ResponseCodes.MEETING_COMPLETED, i18n("meeting.completed")));
                    }
                    if (m.getStatus() == MeetingStatus.EXPIRED) {
                        return ResponseEntity.status(HttpStatus.GONE)
                                .body(MeetingResponse.error(ResponseCodes.MEETING_EXPIRED, i18n("meeting.expired")));
                    }
                    // If not started yet and user is not the host, don’t mint a token
                    if (m.getStatus() != MeetingStatus.STARTED) {
                        return ResponseEntity.ok(MeetingResponse.success(
                                ResponseCodes.MEETING_NOT_STARTED, "Host hasn’t started yet", mapper.toDto(m)));
                    }
                    // If meeting requires password but none provided -> ask client for passcode (200 + success envelope)
                    // If meeting requires password:
                    // - no password supplied -> tell client to prompt for passcode (200 + success envelope).
                    // - password supplied but incorrect -> 403 PASSWORD_INCORRECT

                    if (req.token() != null && !req.token().isBlank() &&!req.token().trim().equals(m.getToken())){
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED,
                                        i18n("meeting.access.denied")));
                    }

                    if (req.token() != null && !req.token().trim().isBlank()) {
                        if (m.getToken().equals(req.token().trim())) {
                            var existing = tokenRegistry.getIfActive(m.getId(), guestKey);
                            existing.ifPresent(t -> tokenRegistry.revoke(m.getId(), guestKey));

                            var gen = algomeetJwtService.generateForMeeting(
                                    m, guestKey, (req.name() == null ? "" : req.name().trim()), null, false);

                            tokenRegistry.save(m.getId(), guestKey, gen.token(), Duration.ofSeconds(300));
                            var dto = mapper.toDto(m);
                            return ResponseEntity.ok(MeetingResponse.success(
                                    ResponseCodes.MEETING_JOINED_SUCCESS, "You can join now.",
                                    new OpenMeetingJoinResponse(dto, gen.token(), gen.room(), gen.exp())
                            ));
                        } else
                            return ResponseEntity.status(HttpStatus.FORBIDDEN);
                    } else if(req.password() == null || req.password().isBlank()) {
                        return ResponseEntity.ok(MeetingResponse.success(
                                ResponseCodes.PASSWORD_REQUIRED,
                                i18n("meeting.password.required"),
                                mapper.toDto(m)
                        ));
                    }
                    if (!meetingService.verifyPassword(m, req.password())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(MeetingResponse.error(ResponseCodes.PASSWORD_INCORRECT, i18n("meeting.password.incorrect")));
                    }

                    var existing = tokenRegistry.getIfActive(m.getId(), guestKey);
                    existing.ifPresent(t -> tokenRegistry.revoke(m.getId(), guestKey));

                    var gen = algomeetJwtService.generateForMeeting(
                            m, guestKey, (req.name() == null ? "" : req.name().trim()), null, false);

                    tokenRegistry.save(m.getId(), guestKey, gen.token(), Duration.ofSeconds(300));

                    var dto = mapper.toDto(m);
                    return ResponseEntity.ok(MeetingResponse.success(
                            ResponseCodes.MEETING_JOINED_SUCCESS, "You can join now.",
                            new OpenMeetingJoinResponse(dto, gen.token(), gen.room(), gen.exp())
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED,
                                "Unauthorized, invalid token, or meeting unavailable")));
    }

    /*@GetMapping("/open/{id}")
    public ResponseEntity<?> getOpenMeeting(
            @PathVariable String id,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String password,
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
                    .map(m -> {
                        switch (m.getStatus()) {
                            case STARTED: {
                                // If meeting has password enabled, enforce it for guests as well.
                                if (m.isPasswordEnabled()) {
                                    // If no password supplied -> tell client passcode is required
                                    if (password == null || password.isBlank()) {
                                        return ResponseEntity.ok(MeetingResponse.success(
                                                ResponseCodes.PASSWORD_REQUIRED,
                                                i18n("meeting.password.required"),
                                                mapper.toDto(m)
                                        ));
                                    }
                                    // If password supplied but incorrect -> deny
                                    if (!meetingService.verifyPassword(m, password)) {
                                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                .body(MeetingResponse.error("PASSWORD_INCORRECT", i18n("meeting.password.incorrect")));
                                    }
                                    // else password OK -> continue to mint token
                                }

                                var existing = tokenRegistry.getIfActive(m.getId(), guestKey);
                                existing.ifPresent(t -> tokenRegistry.revoke(m.getId(), guestKey));

                                String displayName = (userName != null && !userName.isBlank())
                                        ? userName.trim()
                                        : (name != null ? name.trim() : "");

                                boolean moderator = false;

                                var gen = algomeetJwtService.generateForMeeting(
                                        m, guestKey, displayName, null, moderator);

                                tokenRegistry.save(m.getId(), guestKey, gen.token(), java.time.Duration.ofSeconds(300));

                                var dto = mapper.toDto(m);
                                return ResponseEntity.ok(MeetingResponse.success(
                                        ResponseCodes.MEETING_JOINED_SUCCESS, i18n("mee"),
                                        new OpenMeetingJoinResponse(dto, gen.token(), gen.room(), gen.exp())
                                ));
                            }
                            case SCHEDULED:
                                return ResponseEntity.ok(MeetingResponse.success(
                                        ResponseCodes.MEETING_NOT_STARTED, "Host hasn’t started the meeting yet.", mapper.toDto(m)));
                            case COMPLETED:
                                return ResponseEntity.status(HttpStatus.GONE)
                                        .body(MeetingResponse.error(ResponseCodes.MEETING_COMPLETED, "This meeting is over."));
                            case EXPIRED:
                                return ResponseEntity.status(HttpStatus.GONE)
                                        .body(MeetingResponse.error(ResponseCodes.MEETING_EXPIRED, i18n("meeting.expired")));
                            default:
                                return ResponseEntity.ok(MeetingResponse.success(ResponseCodes.MEETING_FETCH_SUCCESS, i18n("meeting.fetch.success"), mapper.toDto(m)));
                        }
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED,
                                    "Unauthorized, invalid token, or meeting unavailable")));

        } catch (AccessDeniedException e) {
            log.warn("OpenMeeting access denied: id={}, reason={}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED, e.getMessage()));
        } catch (Exception e) {
            log.error("OpenMeeting error: id={}, ex={}", id, e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MeetingResponse.error("INTERNAL_ERROR", "Unexpected error while fetching open meeting"));
        }
    }*/

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
    public ResponseEntity<MeetingResponse<List<MeetingDto>>> getMyMeetings() {
        String email = currentUser();
        log.info("GetMyMeetings request by user={}", maskEmail(email));
        List<Meeting> meetings = meetingService.getMeetingsForUser(email);
        log.info("GetMyMeetings: user={}, count={}", maskEmail(email), meetings.size());
        List<MeetingDto> dtos = meetings.stream().map(mapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(MeetingResponse.success("OK", "Fetched meetings", dtos));
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
                    "code", ResponseCodes.MEETING_COMPLETED,
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
                    .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED, ade.getMessage()));
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
                    .body(MeetingResponse.error(ResponseCodes.MEETING_ACCESS_DENIED, ade.getMessage()));
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

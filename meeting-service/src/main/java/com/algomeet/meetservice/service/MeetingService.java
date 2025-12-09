package com.algomeet.meetservice.service;

import com.algomeet.meetservice.Dto.EditMeetingRequest;
import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.enums.MeetingType;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.model.Meeting;

import com.algomeet.meetservice.model.MeetingStatus;
import com.algomeet.meetservice.model.Room;
import com.algomeet.meetservice.model.RoomType;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.repository.RoomRepository;
import com.algomeet.meetservice.util.MeetingIdGenerator;
import com.algomeet.meetservice.util.MeetingRoomIdAllocator;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.service.NotificationService;

import feign.FeignException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MeetingIdGenerator idGen;

    @Autowired
    private NotificationService notificationService;

    @Value("${meeting.expiration.minutes:60}")
    private int expirationMinutes;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserDirectoryClient userDirectoryClient;

    @Autowired
    private MeetingRoomIdAllocator meetingRoomIdAllocator;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LinkFactory linkFactory;

    private static final SecureRandom RANDOM = new SecureRandom();

    public Meeting createMeeting(String email, MeetingRequest request) {
        // 0) Basic validation
        if (request.getMeetingStartTime() != null && request.getMeetingEndTime() != null
                && request.getMeetingEndTime().isBefore(request.getMeetingStartTime())) {
            throw new IllegalArgumentException("meetingEndTime cannot be before meetingStartTime");
        }

        // 1) Resolve host from user-directory (email/username/user_key supported by /lookup/exact)
        UserDirectoryClient.User host = null;
        try {
            host = userDirectoryClient.exact(email);
            if (host == null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Host not found");
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Host not found");
        }
        final String tenantId = host.tenantId();
        final UUID hostUserKey = host.userKey(); // only if your Meeting has this column
        final String hostEmail = host.email() != null ? host.email() : email;
        final  String hostName = host.username() != null ? host.username() : host.email();

        // 2) Resolve/choose Room (12-digit id)
        final boolean usePersonalRoom = Boolean.TRUE.equals(request.getUsePersonalRoom());
        final Room room;
        if (usePersonalRoom) {
            // use the host’s existing personal room id and make sure a row exists in rooms table
            Room pr = host.personalRoom();
            if (pr == null) {
                throw new IllegalStateException("Host does not have a personal room yet");
            }

            room = roomRepository.findById(pr.getRoomId()).orElseGet(() ->
                    roomRepository.save(
                            Room.builder()
                                    .roomId(pr.getRoomId())
                                    .roomType(RoomType.PERSONAL)
                                    .tenantId(tenantId)
                                    .ownerUserId(hostUserKey)
                                    .ownerEmail(hostEmail)
                                    .lobbyDefault(false)
                                    .recordingDefault(false)
                                    .createdAt(Instant.now())
                                    .build()
                    )
            );
        } else {
            // allocate a new ADHOC room for this meeting
            room = meetingRoomIdAllocator.allocateForTenant(tenantId);
            roomRepository.save(room);
        }

        // 3) Generate meeting id / token / expiry
        String id = idGen.nextId();
        String token = UUID.randomUUID().toString();
        Instant meetingEndTime = request.getMeetingEndTime();
        Instant expiry = meetingEndTime.plus(expirationMinutes, ChronoUnit.MINUTES);

        log.info("CreateMeeting: host={}, tenant={}, usePersonalRoom={}, expiresInMin={}, attendeesCount={}",
                maskEmail(hostEmail), tenantId, usePersonalRoom, expirationMinutes,
                request.getAttendees() == null ? 0 : request.getAttendees().size());
        log.debug("CreateMeeting: id={}, tokenLen={}, type={}, lobbyEnabled={}, reminderEnabled={}, roomId={}",
                id, token.length(), request.getMeetingType(), request.isLobbyEnabled(), request.isReminderEnabled(), room.getRoomId());

        // 4) Build entity
        Meeting meeting = new Meeting();
        meeting.setId(id);
        meeting.setToken(token);
        meeting.setCreatedAt(Instant.now());
        meeting.setExpiresAt(expiry);

        meeting.setHostEmail(hostEmail);
        meeting.setHostName(hostName);
        // if your Meeting entity has this column + setter:
        try {
            meeting.getClass().getMethod("setHostUserKey", UUID.class);
            meeting.setHostUserKey(hostUserKey);
        } catch (NoSuchMethodException ignored) {
        }

        meeting.setStatus(MeetingStatus.SCHEDULED);

        meeting.setPasswordEnabled(request.isPasswordEnabled());
        if (request.isPasswordEnabled()) {
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new IllegalArgumentException("Password enabled but no password provided");
            }
            //meeting.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            meeting.setPasswordHash(request.getPassword());
        } else {
            meeting.setPasswordHash(null);
        }
        meeting.setMeetingName(request.getMeetingName());
        meeting.setMeetingStartTime(request.getMeetingStartTime());
        meeting.setMeetingEndTime(request.getMeetingEndTime());
        meeting.setMeetingDescription(request.getMeetDescription());
        meeting.setMeetingType(request.getMeetingType());

        // persist chosen room
        meeting.setRoom(room);

        // Options
        meeting.setRecurrence(request.getRecurrence());
        meeting.setReminderEnabled(request.isReminderEnabled());
        meeting.setReminderMinutes(request.getReminderMinutes());
        meeting.setLobbyEnabled(request.isLobbyEnabled());

        // Participants
        Set<String> attendees = request.getAttendees() != null
                ? new HashSet<>(request.getAttendees())
                : new HashSet<>();
        meeting.setInvitedParticipants(attendees);
        meeting.setAttendees(new HashSet<>(attendees));
        meeting.setPendingParticipants(new HashSet<>());

        // 5) Save meeting
        Meeting savedMeeting = meetingRepository.save(meeting);
        log.info("CreateMeeting success: id={}, roomId={}, host={}, status={}, startTime={}, endTime={}",
                savedMeeting.getId(), savedMeeting.getRoom(), maskEmail(savedMeeting.getHostEmail()),
                savedMeeting.getStatus(), savedMeeting.getMeetingStartTime(), savedMeeting.getMeetingEndTime());

        // 6) Notify attendees (best-effort)
        try {
            Notification notif = Notification.builder()
                    .receiverGroup(ReceiverGroup.MEETING_ATTENDEES)
                    .receiverGroupRefId(savedMeeting.getId())
                    .type(NotificationType.MEETING_INVITE)
                    .title("You have received a meeting invite")
                    .body(savedMeeting.getMeetingName())
                    .data(Map.of("meetingId", savedMeeting.getId(), "room", savedMeeting.getRoom()))
                    .deliveryAckRequired(true)
                    .tenantId(TenantContext.getCurrentTenant())
                    .build();
            notificationService.sendPush(notif);
            log.info("Meeting invite notification queued: meetingId={}, attendeesCount={}",
                    savedMeeting.getId(), savedMeeting.getAttendees() == null ? 0 : savedMeeting.getAttendees().size());
        } catch (Exception ex) {
            log.warn("Meeting invite notification failed: meetingId={}, reason={}",
                    savedMeeting.getId(), ex.toString());
        }

        return savedMeeting;
    }

    @Transactional
    public Meeting startIfScheduledByHost(Meeting meeting) {
        if (meeting == null) throw new IllegalArgumentException("meeting is null");
        if (meeting.getStatus() == MeetingStatus.SCHEDULED) {
            meeting.setStatus(MeetingStatus.STARTED);
            meetingRepository.save(meeting);
            log.info("Host started meeting: id={}, newStatus={}", meeting.getId(), meeting.getStatus());
        }
        return meeting;
    }


    // Mark a meeting as COMPLETED
    public boolean markMeetingAsCompleted(String meetingId, String email) {
        log.info("MarkCompleted request: id={}, byHost={}", meetingId, maskEmail(email));
        Optional<Meeting> meetingOpt = meetingRepository.findById(meetingId);
        if (meetingOpt.isPresent()) {
            Meeting meeting = meetingOpt.get();

            if (safeEqIgnoreCase(meeting.getHostEmail(), email)) {
                meeting.setStatus(MeetingStatus.COMPLETED);
                meetingRepository.save(meeting);
                log.info("MarkCompleted success: id={}", meetingId);
                // TODO: notify attendees of meeting end (push/email)
                return true;
            } else {
                log.warn("MarkCompleted denied (not host): id={}, by={}", meetingId, maskEmail(email));
            }
        } else {
            log.warn("MarkCompleted not found: id={}", meetingId);
        }
        return false;
    }

    // Host or attendee (with valid token) access
    public Optional<Meeting> getMeetingById(String id, String email, String token) {
        log.info("GetMeetingById: id={}, by={}", id, maskEmail(email));
        if (token != null)
            log.debug("GetMeetingById: tokenLen={}", token.length());

        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isEmpty()) {
            log.warn("GetMeetingById not found: id={}", id);
            return Optional.empty();
        }

        Meeting m = meetingOpt.get();

        boolean isHost = safeEqIgnoreCase(m.getHostEmail(), email);
        boolean hasValidToken = token != null && hasValidToken(token, m.getToken());
        boolean isAttendee = m.getAttendees() != null && m.getAttendees().stream()
                .anyMatch(a -> safeEqIgnoreCase(a, email));
        log.info("GetMeetingById accessCheck: isHost={}, isAttendee={}, hasValidToken={}, status={}",
                isHost, isAttendee, hasValidToken, m.getStatus());

        // Access gate: host OR (valid token for attendee)
        // NOTE: attendee *does not* bypass token per the new policy (token is mandatory
        // unless the caller is the host). This enforces "token required" when password
        // is not enabled, and allows controller to later enforce password when enabled.
        /*if (!isHost && !hasValidToken) {
            log.info("GetMeetingById denied: id={}, by={}", id, maskEmail(email));
            return Optional.empty(); // 403 in controller
        }*/

        // Hard-block attendees for completed/expired meetings
        if (!isHost && (m.getStatus() == MeetingStatus.COMPLETED || m.getStatus() == MeetingStatus.EXPIRED)) {
            log.info("GetMeetingById blocked (completed/expired) for attendee: id={}, status={}", id, m.getStatus());
            return Optional.empty();
        }

        // Host auto-starts the meeting if not already started & not completed/expired... Disabling this part of the Code JOiN API will be used
        //
        if (isHost) {
            if (m.getStatus() == MeetingStatus.SCHEDULED) {
                m.setStatus(MeetingStatus.STARTED);
                meetingRepository.save(m);
                log.info("Host auto-started meeting: id={}, newStatus={}", id, m.getStatus());
            }
            return Optional.of(m);
        }

        // Attendee path: return meeting as-is (FE handles lobby/wait room)
        log.debug("GetMeetingById attendee allowed: id={}, status={}", id, m.getStatus());
        return Optional.of(m);
    }

    /**
     *  Raw find by id (no access checks). Useful for controllers that want to inspect
     *  meeting properties (e.g. passwordEnabled) to decide how to respond.
     */
    public Optional<Meeting> findMeetingByIdRaw(String id) {
        return meetingRepository.findById(id);
    }

    // Open (guest) access with token only
    public Optional<Meeting> getOpenMeetingById(String id, String token) {
        log.info("GetOpenMeetingById: id={}", id);
        if (token != null)
            log.debug("GetOpenMeetingById: tokenLen={}", token.length());

        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isEmpty()) {
            log.warn("GetOpenMeetingById not found: id={}", id);
            return Optional.empty();
        }

        Meeting m = meetingOpt.get();

        /*if (!hasValidToken(token, m.getToken())) {
            log.warn("GetOpenMeetingById invalid token: id={}", id);
            return Optional.empty();
        }*/

        if (m.getStatus() == MeetingStatus.COMPLETED || m.getStatus() == MeetingStatus.EXPIRED) {
            log.info("GetOpenMeetingById blocked (completed/expired): id={}, status={}", id, m.getStatus());
            return Optional.empty();
        }

        log.debug("GetOpenMeetingById success: id={}, status={}", id, m.getStatus());
        return Optional.of(m);
    }

    private boolean hasValidToken(String provided, String actual) {
        if (provided == null || actual == null) return false;
        // constant-time compare
        byte[] a = provided.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) result |= a[i] ^ b[i];
        return result == 0;
    }

    // Get meetings hosted by user
    public List<Meeting> getMeetingsByHostEmail(String hostEmail) {
        log.debug("GetMeetingsByHostEmail: host={}", maskEmail(hostEmail));
        List<Meeting> list = meetingRepository.findAllByHostEmail(hostEmail);
        log.info("GetMeetingsByHostEmail: host={}, count={}", maskEmail(hostEmail), list.size());
        return list;
    }

    public void deleteExpiredMeetings() {
        Instant now = Instant.now();
        List<Meeting> expiredMeetings = meetingRepository.findByExpiresAtBefore(now);
        log.info("DeleteExpiredMeetings: now={}, expiredCount={}", now, expiredMeetings.size());
        meetingRepository.deleteAll(expiredMeetings);
    }

    // Get all meetings where user is host or attendee
    public List<Meeting> getMeetingsForUser(String email) {
        log.info("GetMeetingsForUser: user={}", maskEmail(email));
        List<Meeting> allMeeting = meetingRepository
                .findDistinctByHostEmailOrAttendeesContainingOrderByMeetingStartTimeAsc(email, email);
        List<Meeting> filtered = allMeeting.stream()
                .filter(m -> m.getMeetingType() == MeetingType.MEETING)
                .toList();
        log.info("GetMeetingsForUser: user={}, total={}, filtered={}",
                maskEmail(email), allMeeting.size(), filtered.size());
        return filtered;
    }

    private void sendEmailInvite(String to, String meetingId, String token) {
        try {
            log.info("EmailInvite queued: to={}, meetingId={}, tokenLen={}", maskEmail(to), meetingId, token == null ? 0 : token.length());
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject("Meeting Invitation");
            // Do NOT log token or URL; send only via email body.
            helper.setText("Join meeting: https://meet.algoframe.in/" + meetingId + "?token=" + token);
            mailSender.send(message);
            log.info("EmailInvite sent: meetingId={}, to={}", meetingId, maskEmail(to));
        } catch (MessagingException e) {
            log.error("EmailInvite failed: meetingId={}, to={}, ex={}", meetingId, maskEmail(to), e.toString(), e);
        }
    }

    public boolean approveParticipant(String meetingId, String hostEmail, String attendeeEmail) {
        log.info("ApproveParticipant: meetingId={}, host={}, attendee={}",
                meetingId, maskEmail(hostEmail), maskEmail(attendeeEmail));

        Optional<Meeting> optional = meetingRepository.findById(meetingId);
        if (optional.isEmpty()) {
            log.warn("ApproveParticipant not found: meetingId={}", meetingId);
            return false;
        }

        Meeting meeting = optional.get();

        if (!safeEqIgnoreCase(meeting.getHostEmail(), hostEmail)) {
            log.warn("ApproveParticipant denied (not host): meetingId={}, by={}", meetingId, maskEmail(hostEmail));
            return false;
        }

        if (meeting.getPendingParticipants().remove(attendeeEmail)) {
            meeting.getAttendees().add(attendeeEmail);
            meetingRepository.save(meeting);
            log.info("ApproveParticipant success: meetingId={}, attendee={}", meetingId, maskEmail(attendeeEmail));
            return true;
        }

        log.warn("ApproveParticipant not pending: meetingId={}, attendee={}", meetingId, maskEmail(attendeeEmail));
        return false;
    }

    public boolean rejectParticipant(String meetingId, String hostEmail, String attendeeEmail) {
        log.info("RejectParticipant: meetingId={}, host={}, attendee={}",
                meetingId, maskEmail(hostEmail), maskEmail(attendeeEmail));

        Optional<Meeting> optional = meetingRepository.findById(meetingId);
        if (optional.isEmpty()) {
            log.warn("RejectParticipant not found: meetingId={}", meetingId);
            return false;
        }

        Meeting meeting = optional.get();

        if (!safeEqIgnoreCase(meeting.getHostEmail(), hostEmail)) {
            log.warn("RejectParticipant denied (not host): meetingId={}, by={}", meetingId, maskEmail(hostEmail));
            return false;
        }

        if (meeting.getPendingParticipants() != null && meeting.getPendingParticipants().remove(attendeeEmail)) {
            // optionally track rejection or notify user
            meetingRepository.save(meeting);
            log.info("RejectParticipant success: meetingId={}, attendee={}", meetingId, maskEmail(attendeeEmail));
            return true;
        }

        log.warn("RejectParticipant not pending: meetingId={}, attendee={}", meetingId, maskEmail(attendeeEmail));
        return false;
    }

    @Transactional
    public Meeting updateMeeting(String email, String id, EditMeetingRequest req) {
        log.info("UpdateMeeting: id={}, by={}", id, maskEmail(email));

        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Meeting not found"));

        if (!safeEqIgnoreCase(m.getHostEmail(), email)) {
            log.warn("UpdateMeeting denied (not host): id={}, by={}", id, maskEmail(email));
            throw new AccessDeniedException("Only host can edit");
        }
        if (m.getExpiresAt() != null && m.getExpiresAt().isBefore(Instant.now())) {
            log.warn("UpdateMeeting denied (expired): id={}", id);
            throw new AccessDeniedException("Meeting already expired");
        }
        if (m.getStatus() != null && m.getStatus() != MeetingStatus.SCHEDULED) {
            log.warn("UpdateMeeting denied (not scheduled): id={}, status={}", id, m.getStatus());
            throw new AccessDeniedException("Only scheduled meetings can be edited");
        }

        // Track which fields changed (no sensitive contents logged)
        boolean changed = false;

        if (req.getMeetingName() != null) {
            m.setMeetingName(req.getMeetingName());
            changed = true;
        }
        if (req.getMeetDescription() != null) {
            m.setMeetingDescription(req.getMeetDescription());
            changed = true;
        }
        if (req.getMeetingStartTime() != null) {
            m.setMeetingStartTime(req.getMeetingStartTime());
            changed = true;
        }
        if (req.getMeetingEndTime() != null) {
            m.setMeetingEndTime(req.getMeetingEndTime());
            changed = true;
        }
        if (req.getRecurrence() != null) {
            m.setRecurrence(req.getRecurrence());
            changed = true;
        }
        if (req.getReminderEnabled() != null) {
            m.setReminderEnabled(req.getReminderEnabled());
            changed = true;
        }
        if (req.getReminderMinutes() != null) {
            m.setReminderMinutes(req.getReminderMinutes());
            changed = true;
        }
        if (req.getLobbyEnabled() != null) {
            m.setLobbyEnabled(req.getLobbyEnabled());
            changed = true;
        }
        if (req.getAttendees() != null) {
            m.setInvitedParticipants(new HashSet<>(req.getAttendees()));
            changed = true;
        }

        Meeting saved = meetingRepository.save(m);
        log.info("UpdateMeeting success: id={}, changed={}", id, changed);
        // TODO: send update notifications to participants if needed
        return saved;
    }

    @Transactional
    public void deleteMeeting(String email, String id) {
        log.info("DeleteMeeting: id={}, by={}", id, maskEmail(email));
        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Meeting not found"));

        if (!safeEqIgnoreCase(m.getHostEmail(), email)) {
            log.warn("DeleteMeeting denied (not host): id={}, by={}", id, maskEmail(email));
            throw new AccessDeniedException("Only host can delete");
        }

        meetingRepository.delete(m);
        log.info("DeleteMeeting success: id={}", id);
    }

    /* ----------------- helpers ----------------- */

    private static boolean safeEqIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    // Avoid logging full PII or secrets
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "unknown";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 4) + "***";
        return maskedLocal + "@" + domain;
    }

    /**
     * Server-side check for participant-supplied password (host never needs it).
     */
    public boolean verifyPassword(Meeting m, String supplied) {
        if (!m.isPasswordEnabled())
            return true;
        if (supplied == null || supplied.isBlank())
            return false;
        return supplied.equals(m.getPasswordHash());
    }
}

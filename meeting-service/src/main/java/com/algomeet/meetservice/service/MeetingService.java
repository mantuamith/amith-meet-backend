package com.algomeet.meetservice.service;

import com.algomeet.meetservice.Dto.EditMeetingRequest;
import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.enums.MeetingType;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.MeetingStatus;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.util.RandomIdGenerator;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.service.NotificationService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MeetingService {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private NotificationService notificationService;

    @Value("${meeting.expiration.minutes:60}")
    private int expirationMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public Meeting createMeeting(String email, MeetingRequest request) {
        //String id = generateReadableId();
        String id = RandomIdGenerator.generateId();
        String token = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES);


        Meeting meeting = new Meeting();
        meeting.setId(id);
        meeting.setToken(token);
        meeting.setCreatedAt(Instant.now());
        meeting.setExpiresAt(expiry);
        meeting.setHostEmail(email);

        // Status is always SCHEDULED at creation
        meeting.setStatus(MeetingStatus.SCHEDULED);

        meeting.setPassword(request.getPassword());
        meeting.setMeetingName(request.getMeetingName());
        meeting.setMeetingStartTime(request.getMeetingStartTime());
        meeting.setMeetingEndTime(request.getMeetingEndTime());
        meeting.setMeetingDescription(request.getMeetDescription());
        meeting.setInvitedParticipants(request.getAttendees() != null ? new HashSet<>(request.getAttendees()) : new HashSet<>());

        meeting.setRecurrence(request.getRecurrence());
        meeting.setReminderEnabled(request.isReminderEnabled());
        meeting.setReminderMinutes(request.getReminderMinutes());
        meeting.setLobbyEnabled(request.isLobbyEnabled());
        meeting.setPendingParticipants(new HashSet<>());
        meeting.setMeetingType(request.getMeetingType());

        //if(!request.isLobbyEnabled()){
            meeting.setAttendees(new HashSet<>(request.getAttendees()));
        /*}else {
            System.out.println("Lobby is Enabled");
            meeting.setAttendees(new HashSet<>());
        }*/

        //TODO: Send Notification all users in Attendees that meeting is created

        //sendEmailInvite(email, id, token);
        
        Meeting savedMeeting = meetingRepository.save(meeting);
        
        // Send meeting invite notification to attendees 
        Notification notif = Notification.builder()
        		.receiverGroup(ReceiverGroup.MEETING_ATTENDEES)
        		.receiverGroupRefId(savedMeeting.getId())
        		.type(NotificationType.MEETING_INVITE)
        		.title("You have received a meeting invite")
        		.body(savedMeeting.getMeetingName())
        		.data(Map.of(
        			    "meetingId", savedMeeting.getId()
        			))
        		.deliveryAckRequired(true)
        		.build();
        notificationService.sendPush(notif);
        
        return savedMeeting;
    }

    //Mark a meeting as COMPLETED
    public boolean markMeetingAsCompleted(String meetingId, String email) {
        Optional<Meeting> meetingOpt = meetingRepository.findById(meetingId);
        if (meetingOpt.isPresent()) {
            Meeting meeting = meetingOpt.get();

            // Only host can mark as completed
            if (meeting.getHostEmail().equals(email)) {
                meeting.setStatus(MeetingStatus.COMPLETED);
                meetingRepository.save(meeting);
                return true;
            }
        }
        //TODO: Meeting End Complete Notification
        return false;
    }
    //TODO: JWT Meeting token claims: room , Moderrator
    public Optional<Meeting> getMeetingById(String id, String email, String token) {
        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isEmpty()) return Optional.empty();

        Meeting m = meetingOpt.get();

        boolean isHost = safeEqIgnoreCase(m.getHostEmail(), email);
        boolean hasValidToken = token != null && token.equals(m.getToken());

        // Access gate: host OR (valid token for attendee)
        if (!isHost && !hasValidToken) {
            return Optional.empty(); // 403 in controller
        }

        // Hard-block attendees for completed/expired meetings
        if (!isHost && (m.getStatus() == MeetingStatus.COMPLETED || m.getStatus() == MeetingStatus.EXPIRED)) {
            return Optional.empty(); // 403
        }

        // Host auto-starts the meeting if not already started & not completed/expired
        if (isHost) {
            if (m.getStatus() == MeetingStatus.SCHEDULED) {
                m.setStatus(MeetingStatus.STARTED);
                // (Optional) set timestamps if you track them:
                // m.setStartedAt(Instant.now());
                meetingRepository.save(m);
            }
            return Optional.of(m);
        }

        // --- Attendee path below ---

        // If lobby is enabled, do NOT change status here; host controls admits.
        // We simply return the meeting as-is and let the FE show the lobby/waiting screen.

        // If meeting is started, attendee can proceed
        if (m.getStatus() == MeetingStatus.STARTED) {
            return Optional.of(m);
        }

        // If not started yet (SCHEDULED), attendee can see that it's not started
        if (m.getStatus() == MeetingStatus.SCHEDULED) {
            return Optional.of(m);
        }

        // For any other unexpected status, be conservative
        return Optional.empty();
    }

    private static boolean safeEqIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    //TODO: JWT Meeting token claims: room , Moderrator
    public Optional<Meeting> getOpenMeetingById(String id, String token) {
        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isEmpty()) return Optional.empty();

        Meeting m = meetingOpt.get();

        // Constant-time token check to avoid timing attacks
        if (!hasValidToken(token, m.getToken())) {
            return Optional.empty(); // 403 upstream
        }

        // Hard-block if completed/expired
        if (m.getStatus() == MeetingStatus.COMPLETED || m.getStatus() == MeetingStatus.EXPIRED) {
            return Optional.empty(); // 403 upstream
        }

        // Don’t mutate status here (anonymous path). Host is authoritative for starting/ending.
        // - If SCHEDULED => FE shows "not started yet".
        // - If STARTED   => FE shows join screen.
        // - If lobbyEnabled => FE shows lobby wait (host admits).
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


    //Get meetings hosted by user
    public List<Meeting> getMeetingsByHostEmail(String hostEmail) {
        return meetingRepository.findAllByHostEmail(hostEmail);
    }

    public void deleteExpiredMeetings() {
        Instant now = Instant.now();
        List<Meeting> expiredMeetings = meetingRepository.findByExpiresAtBefore(now);
        meetingRepository.deleteAll(expiredMeetings);
    }

    // Get all meetings where user is host or attendee
    public List<Meeting> getMeetingsForUser(String email) {

        List<Meeting> allMeeting =
                meetingRepository.findDistinctByHostEmailOrAttendeesContainingOrderByMeetingStartTimeAsc(email, email);

        return allMeeting.stream()
                .filter(m -> m.getMeetingType() == MeetingType.MEETING) // adjust enum name if yours differs
                .toList();
    }


    private String randomFrom(List<String> words) {
        return words.get(RANDOM.nextInt(words.size()));
    }

    private void sendEmailInvite(String to, String meetingId, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject("Meeting Invitation");
            helper.setText("Join meeting: https://meet.algoframe.in/" + meetingId + "?token=" + token);
            mailSender.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    public boolean approveParticipant(String meetingId, String hostEmail, String attendeeEmail) {
        Optional<Meeting> optional = meetingRepository.findById(meetingId);
        if (optional.isEmpty()) return false;

        Meeting meeting = optional.get();

        // Ensure only host can approve
        if (!meeting.getHostEmail().equals(hostEmail)) return false;

        if (meeting.getPendingParticipants().remove(attendeeEmail)) {
            meeting.getAttendees().add(attendeeEmail);
            meetingRepository.save(meeting);
            return true;
        }
        return false;
    }

    public boolean rejectParticipant(String meetingId, String hostEmail, String attendeeEmail) {
        Optional<Meeting> optional = meetingRepository.findById(meetingId);
        if (optional.isEmpty()) return false;

        Meeting meeting = optional.get();

        // Ensure only host can reject
        if (!meeting.getHostEmail().equalsIgnoreCase(hostEmail)) return false;

        if (meeting.getPendingParticipants() != null && meeting.getPendingParticipants().remove(attendeeEmail)) {
            // TODO: optionally track rejection or notify user
            meetingRepository.save(meeting);
            return true;
        }

        return false;
    }

    @Transactional
    public Meeting updateMeeting(String email, String id, EditMeetingRequest req) {
        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Meeting not found"));

        // host-only
        if (!m.getHostEmail().equals(email)) {
            throw new AccessDeniedException("Only host can edit");
        }
        // optional: disallow editing expired/completed
        if (m.getExpiresAt() != null && m.getExpiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("Meeting already expired");
        }
        if (m.getStatus() != null && m.getStatus() != MeetingStatus.SCHEDULED) {
            throw new AccessDeniedException("Only scheduled meetings can be edited");
        }

        // apply partial updates
        if (req.getMeetingName() != null)
            m.setMeetingName(req.getMeetingName());
        if (req.getMeetDescription() != null)
            m.setMeetingDescription(req.getMeetDescription());
        if (req.getMeetingStartTime() != null)
            m.setMeetingStartTime(req.getMeetingStartTime());
        if (req.getMeetingEndTime() != null)
            m.setMeetingEndTime(req.getMeetingEndTime());
        if (req.getRecurrence() != null)
            m.setRecurrence(req.getRecurrence());
        if (req.getReminderEnabled() != null)
            m.setReminderEnabled(req.getReminderEnabled());
        if (req.getReminderMinutes() != null)
            m.setReminderMinutes(req.getReminderMinutes());
        if (req.getLobbyEnabled() != null)
            m.setLobbyEnabled(req.getLobbyEnabled());
        if (req.getAttendees() != null)
            m.setInvitedParticipants(new HashSet<>(req.getAttendees()));

        //Send Notification to update the participents.

        return meetingRepository.save(m);
    }

    @Transactional
    public void deleteMeeting(String email, String id) {
        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new AccessDeniedException("Meeting not found"));

        if (!m.getHostEmail().equals(email)) {
            throw new AccessDeniedException("Only host can delete");
        }

        meetingRepository.delete(m);
    }


}

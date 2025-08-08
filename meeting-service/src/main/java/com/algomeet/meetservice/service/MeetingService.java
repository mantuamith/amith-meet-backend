package com.algomeet.meetservice.service;

import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.MeetingStatus;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.util.RandomIdGenerator;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

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
        meeting.setMeetingTime(request.getMeetingTime());
        meeting.setInvitedParticipants(request.getAttendees() != null ? new HashSet<>(request.getAttendees()) : new HashSet<>());
        meeting.setRecurrence(request.getRecurrence());
        meeting.setReminderEnabled(request.isReminderEnabled());
        meeting.setReminderMinutes(request.getReminderMinutes());
        meeting.setLobbyEnabled(request.isLobbyEnabled());
        meeting.setPendingParticipants(new HashSet<>());
        meeting.setAttendees(new HashSet<>());

        //sendEmailInvite(email, id, token);
        return meetingRepository.save(meeting);
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
        return false;
    }

    public Optional<Meeting> getMeetingById(String id, String email, String token) {
        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isEmpty()) return Optional.empty();

        Meeting meeting = meetingOpt.get();

        boolean isHost = meeting.getHostEmail().equals(email);
        boolean isInvited = meeting.getInvitedParticipants().contains(email);
        boolean isApprovedAttendee = meeting.getAttendees().contains(email);
        boolean isValidToken = token != null && token.equals(meeting.getToken());

        if (!isHost && !isApprovedAttendee && !isValidToken) {
            return Optional.empty();
        }

// If lobby is enabled and user is invited but not approved yet
        if (meeting.isLobbyEnabled() && !isHost && isInvited && !isApprovedAttendee) {
            meeting.getPendingParticipants().add(email);
            meetingRepository.save(meeting);
            throw new AccessDeniedException("Awaiting host approval");
        }

        return Optional.of(meeting);
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

    //Get all meetings where user is host or attendee
    public List<Meeting> getMeetingsForUser(String email) {
        List<Meeting> hostedMeetings = meetingRepository.findAllByHostEmail(email);
        List<Meeting> attendeeMeetings = meetingRepository.findAllByAttendeeEmail(email);

        Set<Meeting> allMeetings = new HashSet<>();
        allMeetings.addAll(hostedMeetings);
        allMeetings.addAll(attendeeMeetings);

        return new ArrayList<>(allMeetings);
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


}

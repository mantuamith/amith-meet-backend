package com.algomeet.meetservice.service;

import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.util.RandomIdGenerator;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MeetingService {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${meeting.expiration.minutes:60}")
    private int expirationMinutes;

    private static final List<String> WORDS_3 = List.of("fox", "hat", "bit", "sun", "zen");
    private static final List<String> WORDS_4 = List.of("zoom", "chat", "meet", "link", "room");

    private static final SecureRandom RANDOM = new SecureRandom();

    public Meeting createMeeting(String email) {
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

        Meeting saved = meetingRepository.save(meeting);
        //sendEmailInvite(email, id, token);
        return saved;
    }

    public Optional<Meeting> getMeetingById(String id, String token) {
        return meetingRepository.findById(id)
                .filter(m -> m.getToken().equals(token))
                .filter(m -> m.getExpiresAt().isAfter(Instant.now()));
    }

    public List<Meeting> getMeetingsByHostEmail(String email) {
        return meetingRepository.findAllByHostEmail(email);
    }

    public void deleteExpiredMeetings() {
        Instant now = Instant.now();
        List<Meeting> expiredMeetings = meetingRepository.findByExpiresAtBefore(now);
        meetingRepository.deleteAll(expiredMeetings);
    }

    private String generateReadableId() {
        return String.format("%s-%s-%s",
                randomFrom(WORDS_4),
                randomFrom(WORDS_3),
                randomFrom(WORDS_4));
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
}

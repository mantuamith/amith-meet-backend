package com.algomeet.meetservice.scheduler;

import com.algomeet.meetservice.repository.MeetingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MeetingCleanupScheduler {

    @Autowired
    private MeetingRepository meetingRepository;

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredMeetings() {
        meetingRepository.findAll().stream()
                .filter(meeting -> meeting.getExpiresAt().isBefore(Instant.now()))
                .forEach(meetingRepository::delete);
    }
}

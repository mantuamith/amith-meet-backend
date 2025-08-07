package com.algomeet.meetservice.scheduler;

import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.MeetingStatus;
import com.algomeet.meetservice.repository.MeetingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Component
public class MeetingCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MeetingCleanupScheduler.class);

    @Autowired
    private MeetingRepository meetingRepository;

    /**
     * Marks expired meetings as EXPIRED instead of deleting them.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void markExpiredMeetings() {
        Instant now = Instant.now();
        List<Meeting> expiredMeetings = meetingRepository.findByExpiresAtBefore(now);

        if (expiredMeetings.isEmpty()) {
            log.debug("[MEETING CLEANUP] No expired meetings found at {}", now);
            return;
        }

        expiredMeetings.forEach(meeting -> {
            if (meeting.getStatus() != MeetingStatus.EXPIRED) {
                meeting.setStatus(MeetingStatus.EXPIRED);
                meetingRepository.save(meeting);
                log.info("[MEETING CLEANUP] Marked meeting {} as EXPIRED (Expired at: {})",
                        meeting.getId(), meeting.getExpiresAt());
            }
        });

        log.info("[MEETING CLEANUP] Updated {} meeting(s) to EXPIRED", expiredMeetings.size());
    }




    /**
     * Scheduled task to delete expired meetings from the database.
     * Runs every 60 seconds.
     *
     * TODO: In future, also notify participants before deletion if required.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredMeetings() {
        Instant now = Instant.now();
        List<Meeting> expiredMeetings = meetingRepository.findByExpiresAtBefore(now);

        if (expiredMeetings.isEmpty()) {
            log.debug("[MEETING CLEANUP] No expired meetings found at {}", now);
            return;
        }

        expiredMeetings.forEach(meeting -> {
            log.info("[MEETING CLEANUP] Deleting expired meeting: {} (Expired at: {})",
                    meeting.getId(), meeting.getExpiresAt());
            meetingRepository.delete(meeting);
        });

        log.info("[MEETING CLEANUP] Removed {} expired meeting(s)", expiredMeetings.size());
    }

    /**
     * Scheduled task that runs every minute to check for upcoming meetings
     * with reminders enabled.
     *
     * TODO: Integrate with notification-service to send device push/email.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void processMeetingReminders() {
        Instant now = Instant.now();
        Instant inOneMinute = now.plusSeconds(60);

        List<Meeting> meetings = meetingRepository.findByMeetingTimeBetween(now, inOneMinute);

        for (Meeting meeting : meetings) {
            // Calculate actual reminder trigger time
            Instant reminderTrigger = meeting.getMeetingTime()
                    .minusSeconds(meeting.getReminderMinutes() * 60L);

            // If current time is at/after trigger, send reminder
            if (!now.isBefore(reminderTrigger)) {
                // TODO: Call notification-service API here
                System.out.println("[REMINDER PLACEHOLDER] Would send reminder for meeting: "
                        + meeting.getMeetingName() + " to attendees: " + meeting.getAttendees());
                // TODO: Call notification-service to send push notifications
                // notificationService.sendMeetingReminder(meeting.getId(), meeting.getAttendees());
            }
        }
    }
}

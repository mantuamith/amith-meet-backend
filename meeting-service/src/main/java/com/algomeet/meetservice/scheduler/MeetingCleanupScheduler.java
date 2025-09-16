package com.algomeet.meetservice.scheduler;

import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.service.NotificationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MeetingCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MeetingCleanupScheduler.class);

    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private NotificationService notificationService;

    /**
     * Marks expired meetings as EXPIRED instead of deleting them.
     * Runs every 60 seconds.
     */
    /*@Scheduled(fixedRate = 60000)
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
    }*/




    /**
     * Scheduled task to delete expired meetings from the database.
     * Runs every 60 seconds.
     *
     * TODO: In future, also notify participants before deletion if required.
     */
   /* @Scheduled(fixedRate = 60000)
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
    }*/

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
        
        List<Meeting> meetings = meetingRepository.findMeetingsByReminderTimeBetween(now, inOneMinute);
        for (Meeting meeting : meetings) {
        	log.info("Meeting starts: {} , Meeting ends: {}", meetings.get(0).getMeetingStartTime(), meetings.get(0).getMeetingEndTime());       	

        	Notification notif = Notification.builder()
        			.type(NotificationType.MEETING_REMINDER)
        			.receiverGroup(ReceiverGroup.MEETING_PARTICIPANTS)
        			.receiverGroupRefId(meeting.getId())
        			.title("Meeting reminder")
        			.body("Your meeting starts in " + meeting.getReminderMinutes() + " minutes")
        			.data(Map.of(
        					"meetingId", meeting.getId()
        					))
        			.build();
        	notificationService.sendPush(notif);            
        }
    }
}

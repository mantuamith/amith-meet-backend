package com.algomeet.meetservice.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.meetservice.client.ControlClient;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.service.NotificationService;
import static com.algomeet.meetservice.util.MessageUtil.wrapWithBraces;

@Component
@ConditionalOnProperty(name = "algomeet.cleanupScheduler.enabled", havingValue = "true")
public class MeetingCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MeetingCleanupScheduler.class);
    
    @Autowired
    private ControlClient controlClient;

    @Autowired
    private MeetingRepository meetingRepository;
   
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private RedisTemplate<String, Boolean> redisTemplate;
    
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
    	log.info("Check for upcoming meeting reminders");
    	
    	String lockRedisKey = "meeting:meeting-reminder-scheduler-locked";
    			
    	if (!redisTemplate.opsForValue().setIfAbsent(lockRedisKey, 
    			Boolean.TRUE, Duration.ofSeconds(30))) {
    		return;
    	}
    	
    	// Get active tenants
    	List<Integer> tenantIds = null;
    	try {
			var resp = controlClient.getActiveTenantIds();
			tenantIds = (resp != null && resp.getBody() != null)
					? resp.getBody()
					: List.of();
    		log.info("tenantIds {}", tenantIds);
			if (tenantIds.isEmpty()) {
				log.warn("No tenant IDs available; skipping reminders run");
				return;
			}
    	} catch (Exception ex) {
    		log.error("Error retrieving tenant Ids {}", ex.getMessage(), ex);
    	}
    	
    	// If empty, add 0 tenant Id for public users
    	if (CollectionUtils.isEmpty(tenantIds) 
    			|| !tenantIds.contains(Integer.valueOf(0))) {
    		tenantIds = List.of(0);
    	}
    	
        Instant now = Instant.now();
        Instant inOneMinute = now.plusSeconds(60);
        
        for (Integer tenantId : tenantIds) {
        	//Switch schema
        	TenantContext.switchTenantExplicitly(tenantId);
        	
        	try {
        		List<Meeting> meetings = meetingRepository.findMeetingsByReminderTimeBetween(now, inOneMinute);

        		for (Meeting meeting : meetings) {
        			log.info("Meeting start: {} , meeting end: {}", meeting.getMeetingStartTime(), meeting.getMeetingEndTime());       	

        			Notification notif = Notification.builder()
        					.type(NotificationType.MEETING_REMINDER)
        					.receiverGroup(ReceiverGroup.MEETING_PARTICIPANTS)
        					.receiverGroupRefId(meeting.getId())
        					.title("Meeting reminder")
        					.body("Your meeting starts in " + wrapWithBraces(String.valueOf(meeting.getReminderMinutes())) + " minutes")
        					.data(Map.of(
        							"meetingId", meeting.getId()
        							))
        					.tenantId(TenantContext.getCurrentTenant())
        					.build();
        			notificationService.sendPush(notif);            
        		}
        	} catch (Exception ex) {
        		log.error("Error {}", ex.getMessage(), ex);
        	}
        	
        	// Clean up
        	TenantContext.clear();
        }
        
        //Remove redis lock;
        redisTemplate.delete(lockRedisKey);
    }
}

package com.algomeet.meetservice.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.algomeet.meetservice.model.MeetingStatus;
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
	 *
	 * 🔥 CHANGE:
	 * Added tenant loop so this runs for ALL tenants.
	 * Safe for current setup (tenant = 0).
	 */
	@Scheduled(fixedRate = 60000)
	public void markExpiredMeetings() {

		List<Integer> tenantIds = null;

		try {
			var resp = controlClient.getActiveTenantIds();
			tenantIds = (resp != null && resp.getBody() != null)
					? resp.getBody()
					: List.of();
		} catch (Exception ex) {
			log.error("Error retrieving tenant Ids {}", ex.getMessage(), ex);
			return;
		}

		// fallback to tenant 0
		if (CollectionUtils.isEmpty(tenantIds) || !tenantIds.contains(0)) {
			tenantIds = List.of(0);
		}

		for (Integer tenantId : tenantIds) {

			TenantContext.switchTenantExplicitly(tenantId);

			try {
				Instant now = Instant.now();
				List<Meeting> expiredMeetings = meetingRepository.findByExpiresAtBefore(now);

				if (expiredMeetings.isEmpty()) {
					continue;
				}

				expiredMeetings.forEach(meeting -> {
					if (meeting.getStatus() != MeetingStatus.EXPIRED) {
						meeting.setStatus(MeetingStatus.EXPIRED);
						meetingRepository.save(meeting);

						log.info("[MEETING CLEANUP] Marked meeting {} as EXPIRED (Expired at: {})",
								meeting.getId(), meeting.getExpiresAt());
					}
				});

			} catch (Exception ex) {
				log.error("Error processing tenant {} in markExpiredMeetings", tenantId, ex);
			} finally {
				TenantContext.clear();
			}
		}
	}

	/**
	 * Deletes expired meetings from DB.
	 * Runs every 60 seconds.
	 *
	 * 🔥 CHANGE:
	 * Added tenant loop so cleanup runs across all tenants.
	 */
	@Scheduled(fixedRate = 60000)
	public void cleanupExpiredMeetings() {

		List<Integer> tenantIds = null;

		try {
			var resp = controlClient.getActiveTenantIds();
			tenantIds = (resp != null && resp.getBody() != null)
					? resp.getBody()
					: List.of();
		} catch (Exception ex) {
			log.error("Error retrieving tenant Ids {}", ex.getMessage(), ex);
			return;
		}

		// fallback to tenant 0
		if (CollectionUtils.isEmpty(tenantIds) || !tenantIds.contains(0)) {
			tenantIds = List.of(0);
		}

		for (Integer tenantId : tenantIds) {

			TenantContext.switchTenantExplicitly(tenantId);

			try {
				Instant now = Instant.now();
				List<Meeting> expiredMeetings = meetingRepository.findByExpiresAtBefore(now);

				if (expiredMeetings.isEmpty()) {
					continue;
				}

				expiredMeetings.forEach(meeting -> {
					log.info("[MEETING CLEANUP] Deleting expired meeting: {} (Expired at: {})",
							meeting.getId(), meeting.getExpiresAt());

					if (meeting.getStatus() == MeetingStatus.EXPIRED) {
						meetingRepository.delete(meeting);
					}
				});

				log.info("[MEETING CLEANUP] Removed {} expired meeting(s) for tenant {}",
						expiredMeetings.size(), tenantId);

			} catch (Exception ex) {
				log.error("Error processing tenant {} in cleanupExpiredMeetings", tenantId, ex);
			} finally {
				TenantContext.clear();
			}
		}
	}

	/**
	 * Scheduled task for meeting reminders (UNCHANGED)
	 */
	@Scheduled(fixedRate = 60000)
	public void processMeetingReminders() {
		log.info("Check for upcoming meeting reminders");

		String lockRedisKey = "meeting:meeting-reminder-scheduler-locked";

		if (!redisTemplate.opsForValue().setIfAbsent(lockRedisKey,
				Boolean.TRUE, Duration.ofSeconds(30))) {
			return;
		}

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

		if (CollectionUtils.isEmpty(tenantIds)
				|| !tenantIds.contains(Integer.valueOf(0))) {
			tenantIds = List.of(0);
		}

		Instant now = Instant.now();
		Instant inOneMinute = now.plusSeconds(60);

		for (Integer tenantId : tenantIds) {
			TenantContext.switchTenantExplicitly(tenantId);

			try {
				List<Meeting> meetings = meetingRepository.findMeetingsByReminderTimeBetween(now, inOneMinute);

				for (Meeting meeting : meetings) {

					Notification notif = Notification.builder()
							.type(NotificationType.MEETING_REMINDER)
							.receiverGroup(ReceiverGroup.MEETING_PARTICIPANTS)
							.receiverGroupRefId(meeting.getId())
							.title("Meeting reminder")
							.body("Your meeting starts in " + wrapWithBraces(String.valueOf(meeting.getReminderMinutes())) + " minutes")
							.data(Map.of("meetingId", meeting.getId()))
							.tenantId(TenantContext.getCurrentTenant())
							.build();

					notificationService.sendPush(notif);
				}
			} catch (Exception ex) {
				log.error("Error {}", ex.getMessage(), ex);
			}

			TenantContext.clear();
		}

		redisTemplate.delete(lockRedisKey);
	}
}
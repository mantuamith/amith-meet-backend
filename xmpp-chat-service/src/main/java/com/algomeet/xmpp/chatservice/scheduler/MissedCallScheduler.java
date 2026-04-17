package com.algomeet.xmpp.chatservice.scheduler;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.CallSessionMetadata;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>Missed Call Background Worker</h2>
 * <p>
 * The {@code MissedCallScheduler} is a high-precision background worker designed to handle the 
 * lifecycle of unanswered Jingle sessions across a distributed cluster. It ensures that if a user 
 * does not answer a call within the ringing timeout, both parties receive appropriate XMPP stanzas 
 * and push notifications.
 * </p>
 * * <h3>Distributed Locking Mechanism:</h3>
 * <p>
 * To prevent multiple cluster nodes from processing the same timeout, this worker uses an atomic 
 * Redis {@code ZREM} operation. Only the node that successfully removes the Session ID (SID) 
 * from the {@code DELAYED_QUEUE} earns the right to process the missed call.
 * </p>
 * * @author AlgoMeet Core Team
 */
@Slf4j
@Component
@AllArgsConstructor
public class MissedCallScheduler {

	private final StringRedisTemplate redisTemplate;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;
	private final NotificationService notificationService;
	private final GroupCacheService groupCacheService;
	private final XmppArchiveService xmppArchiveService;
	private final JidUtil jidUtil;
	private final UserSessionRegistry userSessionRegistry;

	/**
	 * Periodic task that scans the Redis Sorted Set (ZSET) for expired call sessions.
	 * <p>
	 * The score in the ZSET represents the epoch timestamp when the call should time out.
	 * This method runs every second to maintain real-time responsiveness.
	 * </p>
	 * * @see CallSessionRedisKey#DELAYED_QUEUE
	 */
	@Scheduled(fixedDelay = 1000)
	public void processExpiredCalls() {
		long now = System.currentTimeMillis();

		// Step 1: Query for all SIDs whose timeout (score) is less than or equal to 'now'
		Set<String> expiredSids = redisTemplate.opsForZSet().rangeByScore(CallSessionRedisKey.DELAYED_QUEUE.getVal(), 0, now);

		if (expiredSids == null || expiredSids.isEmpty()) {
			return;
		}

		for (String sid : expiredSids) {
			try {
				/*
				 * Step 2: Distributed Lock Attempt.
				 * Since 'remove' is atomic, only one instance in the cluster will receive a value > 0.
				 * This effectively handles concurrency without needing complex Redlock implementations.
				 */
				Long removed = redisTemplate.opsForZSet().remove(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid);

				if (removed != null && removed > 0) {
					processMissedCall(sid);
				}
			} catch(Exception ex){
				log.error("Critical error in MissedCall task for SID {}: {}", sid, ex.getMessage(), ex);
			}
		}
	}

	/**
	 * Orchestrates the missed call workflow: metadata retrieval, XMPP stanza generation, 
	 * and push notification dispatch.
	 * * @param sid The unique Jingle Session ID retrieved from the timeout queue.
	 */
	private void processMissedCall(String sid) {
		String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);

		// Step 3: Retrieve metadata stored during the initial session-initiate
		Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);

		if (metadata.isEmpty()) {
			log.warn("Missed call metadata missing for SID: {}. It may have been cleaned up elsewhere.", sid);
			return;
		}

		// Extracting context-specific metadata
		String toJid = (String) metadata.get(CallSessionMetadata.TO.getKey());
		String fromJid = (String) metadata.get(CallSessionMetadata.FROM.getKey());
		String type = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());
		String tenantId = (String) metadata.get(CallSessionMetadata.TENANT_ID.getKey());
		String username = (String) metadata.get(CallSessionMetadata.USERNAME.getKey());
		String groupId = (String) metadata.get(CallSessionMetadata.GROUP_ID.getKey());
		
		// Set tenant Id to support multi-tenancy 
		TenantContext.setCurrentTenant(Integer.parseInt(tenantId));

		log.info("Processing missed call log for SID: {} ({} -> {})", sid, fromJid, toJid);

		String toUserKey = XmppUtil.getUserKey(toJid);
		String title = "Missed " + type + " Call";
		String body = String.format("Missed %s call from %s", type, username);

		// Check recipient's current connection status
		Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
		boolean hasActiveSession = !CollectionUtils.isEmpty(userSessions) && userSessions.stream()
				.anyMatch(s -> UserState.ACTIVE == s.getState());

		/*
		 * Step 4: Dispatch Logic.
		 * Differentiates between 1-on-1 calls and Group (MUC) calls to ensure correct 
		 * stanza addressing and history archiving.
		 */
		if(StringUtils.hasText(groupId)) {
			sendGroupChatMissedCallStanza(fromJid, toJid, sid, type, groupId);
		} else {
			// Notify both parties in a 1-on-1 call for consistent history logs
			sendMissedCallStanza(fromJid, toJid, sid, type); // To Callee
			sendMissedCallStanza(toJid, fromJid, sid, type); // To Caller
		}

		// Trigger Push Notification if the user is not actively connected to the XMPP stream
		if (!hasActiveSession) {
			sendPush(toUserKey,  
					"video".equalsIgnoreCase(type) ? NotificationType.VIDEO_MISSED_CALL : NotificationType.AUDIO_MISSED_CALL, 
							title, 
							body, 
							Integer.parseInt(tenantId));
		}

		// Step 5: Clean up metadata immediately to keep Redis memory footprint low
		redisTemplate.delete(metaKey);
	}

	/**
	 * Constructs and routes an XMPP message containing an {@code <call-log/>} extension.
	 * <p>
	 * This method performs two critical tasks:
	 * 1. Persists the log to MongoDB via {@code offlineMessageService} for later retrieval.
	 * 2. Broadcasts the log via Redis Pub/Sub for real-time delivery to online cluster nodes.
	 * </p>
	 */
	private void sendMissedCallStanza(String fromJid, String toJid, String sid, String type) {
		String id = java.util.UUID.randomUUID().toString();
		String timestamp = Instant.now().toString();
		String fromUserKey = XmppUtil.getUserKey(fromJid);
		String toUserKey = XmppUtil.getUserKey(toJid);	

		String xml = String.format(
				"<message from='%s' to='%s' type='chat' id='%s'>" +
						"<subject>Missed %s Call</subject>" +
						"<body>Missed %s call</body>" +
						"<call-log xmlns='urn:xmpp:algomeet:calls' type='%s' status='missed' timestamp='%s' sid='%s'/>" +
						"</message>",
						fromJid, toJid, id, type, type, type, timestamp, sid
				);			

		// Save for offline access (MAM/Offline Store)
		offlineMessageService.save(id, toUserKey, fromUserKey, XmppMessageType.HEADLINE.getXmlValue(), xml)
			.doOnError(e -> log.error("Persistence failed for missed call SID {}: {}", sid, e.getMessage()))
			.subscribe();

		// Real-time broadcast to the recipient's current resource
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, xml);
	}

	/**
	 * Specialized handler for Group (MUC) missed calls.
	 * <p>
	 * Instead of standard offline storage, this uses {@code xmppArchiveService} to ensure the 
	 * missed call event is correctly indexed within the room's permanent history (MAM).
	 * </p>
	 */
	private void sendGroupChatMissedCallStanza(String fromJid, String toJid, String sid, String type, String groupId) {
		String id = java.util.UUID.randomUUID().toString();
		String timestamp = Instant.now().toString();
		String fromUserKey = XmppUtil.getUserKey(fromJid);
		String toUserKey = XmppUtil.getUserKey(toJid);	

		MucRoomDto group = groupCacheService.getCachedGroup(groupId);

		// Identify the caller's MUC nickname for the 'from' attribute
		Optional<MucMember> callerMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(fromUserKey)).findFirst();

		String caller = jidUtil.getGroupBareJid(groupId) + "/" + 
				(callerMucMember.isPresent() ? callerMucMember.get().getUserKey() : "Unknown");

		String xml = String.format(
				"<message from='%s' to='%s' type='groupchat' id='%s'>" +
						"<subject>Missed %s Call</subject>" +
						"<body>Missed %s call</body>" +
						"<call-log xmlns='urn:xmpp:algomeet:calls' type='%s' status='missed' timestamp='%s' sid='%s'/>" +
						"</message>",
						caller, toJid, id, type, type, type, timestamp, sid
				);	

		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
		StanzaInfo info = StanzaInfo.builder()
				.stanzaId(UUID.randomUUID().toString().toLowerCase())
				.build();

		// Archive the event in the Group's message history
		xmppArchiveService.archiveEvent(xml, info, jidUtil.getGroupBareJid(groupId), toUserKey, 
				fromJid, ulidString)
			.doOnError(e -> log.error("Failed to archive MUC call log: {}", e.getMessage()))
			.subscribe();

		// Publish to the cluster
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.GROUPCHAT, xml);
	}

	/**
	 * Sends an out-of-band push notification to mobile devices (FCM/APNs) via the Notification Service.
	 */
	private void sendPush(String to, NotificationType type, String title, String body, Integer tenantId) {        
		Notification notif = Notification.builder()
				.receiverIds(Set.of(to))
				.type(type)
				.title(title)
				.body(body)
				.tenantId(tenantId)
				.build();
		notificationService.sendPush(notif);
	}
}

package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.CallSessionMetadata;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.jingle.JingleTerminationIq;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <h2>Direct Missed Call Background Worker (Reactive)</h2>
 * <p>
 * This worker manages the lifecycle of unanswered Jingle (XEP-0166) sessions. 
 * It monitors a Redis Sorted Set for call timeouts and orchestrates missed call
 * delivery across the cluster.
 * </p>
 * * <h3>Key Reactive Principles applied:</h3>
 * <ul>
 * <li><b>Non-blocking I/O:</b> Uses {@code ReactiveRedisTemplate} to prevent Netty EventLoop saturation.</li>
 * <li><b>Thread Safety:</b> Employs {@code safeUnlock} to handle Redisson thread-affinity issues in asynchronous pipelines.</li>
 * <li><b>Context Isolation:</b> Manages {@code TenantContext} explicitly within elastic schedulers to support multi-tenancy.</li>
 * </ul>
 */
@Slf4j
@Component
@AllArgsConstructor
public class DirectMissedCallService {
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;
	private final NotificationService notificationService;
	private final UserSessionRegistry userSessionRegistry;
	private final RedissonReactiveClient redissonReactiveClient;
	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
	private final CallTrackerService callTrackerService;
	private final UnreadCountService unreadCountService;
	private final DomainProperties domainProperties;

	/**
	 * Scans for expired sessions and acquires a distributed lock to prevent multi-node processing.
	 * * @return A Mono signal indicating completion of the batch process.
	 */
	public Mono<Void> loadMissedCalls(String sid) {
		String lockKey = "xmpp:lock:process:direct-missed-calls:sid:" + sid;
		RLockReactive lock = redissonReactiveClient.getLock(lockKey);

		return Mono.<Void, Boolean>usingWhen(
				// 1. ACQUIRE: Short wait time (300ms) with a safety lease (1s)
				lock.tryLock(300, 1000, TimeUnit.MILLISECONDS),
				acquired -> {
					if (!acquired) {
						return Mono.<Void>empty();
					}

					return processMissedCall(sid);
				},
				// 2. CLEANUP: Safe unlock logic to prevent IllegalMonitorStateException crashes
				acquired -> acquired ? safeUnlock(lock) : Mono.empty(),
						(acquired, err) -> acquired ? safeUnlock(lock) : Mono.empty(),
								acquired -> acquired ? safeUnlock(lock) : Mono.empty()
				);
	}

	/**
	 * Handles Redisson's thread-id sensitivity. In reactive flows, the unlocking thread 
	 * may differ from the locking thread. This method catches ownership exceptions 
	 * to prevent breaking the reactive operator chain.
	 */
	private Mono<Void> safeUnlock(RLockReactive lock) {
		return lock.unlock()
				.onErrorResume(IllegalMonitorStateException.class, e -> {
					log.debug("Lock already released or ownership transferred: {}", e.getMessage());
					return Mono.empty();
				})
				.then();
	}

	/**
	 * The core processing logic for an individual expired call session.
	 * * @param sid The Session ID to process.
	 * @return Mono<Void>
	 */
	private Mono<Void> processMissedCall(String sid) {
		String metaKey = CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid);

		return reactiveRedisTemplate.opsForHash().entries(metaKey)
				.collectMap(
						entry -> entry.getKey().toString(), 
						entry -> entry.getValue().toString()
						)
				.flatMap(metadata -> {
					if (metadata.isEmpty()) {
						log.warn("Missed call metadata missing for SID: {}.", sid);
						return Mono.empty();
					}

					String toJid = (String) metadata.get(CallSessionMetadata.TO_JID.getKey());
					String fromJid = (String) metadata.get(CallSessionMetadata.FROM_JID.getKey());
					String type = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());
					String tenantId = (String) metadata.get(CallSessionMetadata.TENANT_ID.getKey());
					String username = (String) metadata.get(CallSessionMetadata.USERNAME.getKey());

					int tenantIdInt = Integer.parseInt(tenantId);
					String toUserKey = XmppUtil.getUserKey(toJid);

					log.info("Processing missed call SID: {} for user: {}", sid, toUserKey);

					// Wrap EVERYTHING that touches synchronous Redis/Registry into the Runnable
					return Mono.fromRunnable(() -> {
						TenantContext.setCurrentTenant(tenantIdInt);
						try {
							// Move the blocking Registry call INSIDE the protected thread
							Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
							boolean hasActiveSession = !CollectionUtils.isEmpty(userSessions) && userSessions.stream()
									.anyMatch(s -> UserState.ACTIVE == s.getState());

							// Send message to both caller and responder/callee
							sendMissedCallStanza(fromJid, toJid, sid, type);
							sendMissedCallStanza(toJid, fromJid, sid, type);

							// Delete by session ID
							callTrackerService.deleteBySid(sid).subscribe();

							if (!hasActiveSession) {
								sendPush(toUserKey,
										"video".equalsIgnoreCase(type) ? NotificationType.VIDEO_MISSED_CALL : NotificationType.AUDIO_MISSED_CALL,
												"Missed " + type + " Call",
												String.format("Missed %s call from %s", type, username),
												tenantIdInt);
							}	                     
						} finally {
							TenantContext.clear();
						}
					})
							.subscribeOn(Schedulers.boundedElastic()) // This ensures the Runnable doesn't block Netty
							.then(reactiveRedisTemplate.delete(metaKey))
							.then();
				});
	}

	/**
	 * Generates a chat message with a custom 'call-log' extension.
	 * Persists to offline storage for MAM/Archive and publishes to the cluster.
	 */
	private void sendMissedCallStanza(String fromJid, String toJid, String sid, String type) {
		UUID id = UuidCreator.getTimeOrderedEpoch();
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

		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
		// Insert stanza ID
		String forArchiveXml = XmppStanzaUtil.insertStanzaId(xml, ulidString, domainProperties.getDomain());	
			
		offlineMessageService.save(id, toUserKey, fromUserKey, XmppMessageType.HEADLINE.getXmlValue(), forArchiveXml)
		.doOnSuccess(success -> {
			// Publish after successfully saved
			clusterMessagePublisher.convertAndSendToUser(id.toString(), toUserKey, fromUserKey, ChatType.CHAT, forArchiveXml);
			
			// Increment user unread message
			unreadCountService.incrementUnreadCount(fromUserKey, toUserKey);
		})
		.doOnError(e -> log.error("MAM Persistence failed for SID {}: {}", sid, e.getMessage()))
		.subscribe();
		
		// Send timeout message
		String timeoutId = java.util.UUID.randomUUID().toString();
		JingleTerminationIq timeoutStanza = JingleTerminationIq.builder()
				.id(timeoutId)
				.from(fromJid)
				.to(toJid)
				.sid(sid)
				.reason(JingleTerminationIq.REASON_TIMEOUT)
				.build();

		// Publish timeout
		clusterMessagePublisher.convertAndSendToUser(timeoutId, toUserKey, fromUserKey, ChatType.CHAT, timeoutStanza.toXml());
	}

	/**
	 * Out-of-band notification dispatcher for mobile platform delivery.
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
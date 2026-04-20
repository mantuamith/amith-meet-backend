package com.algomeet.xmpp.chatservice.scheduler;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <h2>Missed Call Background Worker (Reactive)</h2>
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
public class MissedCallScheduler {
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;
	private final NotificationService notificationService;
	private final GroupCacheService groupCacheService;
	private final XmppArchiveService xmppArchiveService;
	private final JidUtil jidUtil;
	private final UserSessionRegistry userSessionRegistry;
	private final RedissonReactiveClient redissonReactiveClient;
	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

	/**
	 * Main execution trigger. Subscribes to the reactive chain every second.
	 * Using {@code fixedDelay} ensures that a new execution doesn't start until
	 * the previous reactive subscription has been initialized.
	 */
	@Scheduled(fixedDelay = 1000)
	public void processExpiredCalls() {
		loadMissedCalls().subscribe();
	}

	/**
	 * Scans for expired sessions and acquires a distributed lock to prevent multi-node processing.
	 * * @return A Mono signal indicating completion of the batch process.
	 */
	private Mono<Void> loadMissedCalls() {
	    String lockKey = "xmpp:process-lock:missed-call";
	    RLockReactive lock = redissonReactiveClient.getLock(lockKey);

	    return Mono.<Void, Boolean>usingWhen(
	            // 1. ACQUIRE: Short wait time (300ms) with a safety lease (1s)
	            lock.tryLock(300, 1000, TimeUnit.MILLISECONDS),
	            acquired -> {
	                if (!acquired) {
	                    return Mono.<Void>empty();
	                }

	                long now = System.currentTimeMillis();
	                
	                // 2. QUERY: Fetch all SIDs whose score (timeout) is <= now
	                return reactiveRedisTemplate.opsForZSet()
	                        .rangeByScore(CallSessionRedisKey.DELAYED_QUEUE.getVal(), Range.closed(0.0, (double) now))
	                        .<Void>flatMap(sid -> 
	                            // 3. ATOMIC REMOVE: Only the node that deletes the SID processes it
	                            reactiveRedisTemplate.opsForZSet()
	                                .remove(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid)
	                                .flatMap(removed -> (removed != null && removed > 0) 
	                                    ? processMissedCallReactive(sid) 
	                                    : Mono.<Void>empty())
	                        )
	                        .then();
	            },
	            // 4. CLEANUP: Safe unlock logic to prevent IllegalMonitorStateException crashes
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
	private Mono<Void> processMissedCallReactive(String sid) {
	    String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);

	    // Retrieve the hash map containing JIDs and Tenant info
	    return reactiveRedisTemplate.opsForHash().entries(metaKey)
	            .collectMap(
	                entry -> entry.getKey().toString(), 
	                entry -> entry.getValue().toString()
	            )
	            .flatMap(metadata -> {
	                if (metadata.isEmpty()) {
	                    log.warn("Missed call metadata missing for SID: {}. Likely handled by another pod.", sid);
	                    return Mono.empty();
	                }

	                // Mapping metadata fields
	                String toJid = metadata.get(CallSessionMetadata.TO.getKey());
	                String fromJid = metadata.get(CallSessionMetadata.FROM.getKey());
	                String type = metadata.get(CallSessionMetadata.CALL_TYPE.getKey());
	                String tenantId = metadata.get(CallSessionMetadata.TENANT_ID.getKey());
	                String username = metadata.get(CallSessionMetadata.USERNAME.getKey());
	                String groupId = metadata.get(CallSessionMetadata.GROUP_ID.getKey());

	                int tenantIdInt = Integer.parseInt(tenantId);
	                String toUserKey = XmppUtil.getUserKey(toJid);

	                log.info("Processing missed call SID: {} for user: {}", sid, toUserKey);

	                // Determine if a push notification is required based on connection state
	                Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
	                boolean hasActiveSession = !CollectionUtils.isEmpty(userSessions) && userSessions.stream()
	                        .anyMatch(s -> UserState.ACTIVE == s.getState());

	                // Offload blocking I/O (Stanzas/Push) to the boundedElastic scheduler
	                return Mono.fromRunnable(() -> {
	                    TenantContext.setCurrentTenant(tenantIdInt);
	                    try {
	                        if (StringUtils.hasText(groupId) && Long.parseLong(groupId) > 0) {
	                            sendGroupChatMissedCallStanza(fromJid, toJid, sid, type, groupId);
	                        } else {
	                            // Standard 1-on-1 logic: Notify both caller and callee
	                            sendMissedCallStanza(fromJid, toJid, sid, type);
	                            sendMissedCallStanza(toJid, fromJid, sid, type);
	                        }

	                        if (!hasActiveSession) {
	                            sendPush(toUserKey,
	                                    "video".equalsIgnoreCase(type) ? NotificationType.VIDEO_MISSED_CALL : NotificationType.AUDIO_MISSED_CALL,
	                                    "Missed " + type + " Call",
	                                    String.format("Missed %s call from %s", type, username),
	                                    tenantIdInt);
	                        }
	                    } finally {
	                        TenantContext.clear(); // Critical: Clean up ThreadLocal for pool reuse
	                    }
	                })
	                .subscribeOn(Schedulers.boundedElastic()) 
	                .then(reactiveRedisTemplate.delete(metaKey)) // Final cleanup of call metadata
	                .then();
	            });
	}
	
	/**
	 * Generates a chat message with a custom 'call-log' extension.
	 * Persists to offline storage for MAM/Archive and publishes to the cluster.
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

		offlineMessageService.save(id, toUserKey, fromUserKey, XmppMessageType.HEADLINE.getXmlValue(), xml)
		.doOnError(e -> log.error("MAM Persistence failed for SID {}: {}", sid, e.getMessage()))
		.subscribe();

		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, xml);
	}

	/**
	 * specialized MUC (Multi-User Chat) handler.
	 * Archives events using {@code xmppArchiveService} to ensure visibility in group history.
	 */
	private void sendGroupChatMissedCallStanza(String fromJid, String toJid, String sid, String type, String groupId) {
		String id = java.util.UUID.randomUUID().toString();
		String fromUserKey = XmppUtil.getUserKey(fromJid);
		String toUserKey = XmppUtil.getUserKey(toJid);	

		MucRoomDto group = groupCacheService.getCachedGroup(groupId);
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
						caller, toJid, id, type, type, type, Instant.now().toString(), sid
				);	

		StanzaInfo info = StanzaInfo.builder().stanzaId(UUID.randomUUID().toString().toLowerCase()).build();

		xmppArchiveService.archiveEvent(xml, info, jidUtil.getGroupBareJid(groupId), toUserKey, 
				fromJid, UlidCreator.getMonotonicUlid().toLowerCase())
		.doOnError(e -> log.error("MUC Archive failed: {}", e.getMessage()))
		.subscribe();

		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.GROUPCHAT, xml);
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
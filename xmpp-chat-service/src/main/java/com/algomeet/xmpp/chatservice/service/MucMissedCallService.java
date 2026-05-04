package com.algomeet.xmpp.chatservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.CallSessionMetadata;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.jingle.JingleTerminationIq;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <h2>MUC Missed Call Background Worker (Reactive)</h2>
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
public class MucMissedCallService {
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final NotificationService notificationService;
	private final GroupCacheService groupCacheService;
	private final XmppArchiveService xmppArchiveService;
	private final JidUtil jidUtil;
	private final UserSessionRegistry userSessionRegistry;
	private final RedissonReactiveClient redissonReactiveClient;
	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
	private final MucCallTrackerService mucCallTrackerService;
	private final MucUnreadCountService mucUnreadCountService;
	private final DomainProperties domainProperties;

	/**
	 * Processes a batch of expired MUC sessions by acquiring distributed locks per SID.
	 * After all SIDs in the batch are processed, it verifies if the entire call session 
	 * is complete to send the final missed call notification to the caller.
	 *
	 * @param mucSids List of unique Multi-User Chat session identifiers to process.
	 * @return 
	 * @return A Mono<Void> that signals when the entire batch and post-processing are complete.
	 */
	public Mono<Void> loadMissedCalls(List<String> mucSids) {
		if (CollectionUtils.isEmpty(mucSids)) {
			return Mono.empty();
		}

		// AtomicReference holds metadata from the first session to use for the final notification
		// since specific call details (caller, roomId) are consistent across the same SID.
		AtomicReference<CallSession> referenceCallSession = new AtomicReference<>();

		// Pre-fetch session metadata to populate the reference for the doFinally block.
		// Note: We subscribe here to initiate the fetch immediately.
		return getMucMissedCallSession(mucSids.get(0))
				.doOnNext(referenceCallSession::set)
				.flatMap(next -> {

					return Flux.fromIterable(mucSids)
					.flatMap(mucSid -> {
						
						// Lock for possible competing threads
						// Distributed lock key per mucSid to ensure only one cluster node processes this specific MUC SID
						// Example: a MUC call may contain multiple sessions sharing the same SID but having unique MUC ID,
						// and some missed-call sessions may expire later in Redis and be picked up
						// by another node for processing.
						String lockKey = "xmpp:lock:process:muc-missed-calls:mucsid:" + mucSid;
						RLockReactive lock = redissonReactiveClient.getLock(lockKey);

						return Mono.usingWhen(
								// 1. ACQUIRE: Attempt lock acquisition with a 300ms wait and 1s auto-release safety
								lock.tryLock(0, 3000, TimeUnit.MILLISECONDS),

								acquired -> {
									if (Boolean.FALSE.equals(acquired)) {
										log.debug("Lock busy for mucSid: {}, skipping to avoid duplicate processing", mucSid);
										return Mono.empty();
									}

									// 2. WORK: Proceed with core business logic once lock is secured
									return processMissedCall(mucSid)
											.doOnSuccess(v -> log.info("Successfully processed missed call for: {}", mucSid));
								},

								// 3. RELEASE: Ensure the lock is released in all termination scenarios (Success/Error/Cancel)
								acquired -> Boolean.TRUE.equals(acquired) ? safeUnlock(lock) : Mono.empty(),
										(acquired, err) -> Boolean.TRUE.equals(acquired) ? safeUnlock(lock) : Mono.empty(),
												acquired -> Boolean.TRUE.equals(acquired) ? safeUnlock(lock) : Mono.empty()
								)
								.onErrorResume(e -> {
									// Fail-safe: log individual errors but allow the rest of the Flux to continue
									log.error("Resilient processing failed for mucSid: {}", mucSid, e);
									return Mono.empty(); 
								});
					}, 10) // Concurrency throttle: Prevents overwhelming Redis/Thread pool during 100k+ bursts

					.then(Mono.defer(() -> {
						// This only executes once the Flux completes
						CallSession session = referenceCallSession.get();
						if (session == null) return Mono.empty();	
						
						//  Check Database for remaining records
						return mucCallTrackerService.findFirstBySid(session.getSid())
								// If the DB is empty (no records found), we send the stanza
								.switchIfEmpty(Mono.fromRunnable(() ->  {

									// Acquire a short-lived 5-second lock to prevent duplicate notifications
									// caused by competing threads/nodes processing the same SID.
									// Example: a MUC call may contain multiple sessions sharing the same SID,
									// and some missed-call sessions may expire later in Redis and be picked up
									// by another node for processing.
									String notificationToCallerFlagKey = 
											"xmpp:flag:missed-call-sent:sid:" + session.getSid();

									reactiveRedisTemplate.opsForValue()
									.setIfAbsent(notificationToCallerFlagKey, "true", Duration.ofSeconds(5))
									.filter(isFirst -> isFirst) // Only continue if we are the first node
									.flatMap(isFirst ->  {

										String fromJid = jidUtil.getGroupBareJid(session.getCaller());
										String toJid = jidUtil.getBareJid(session.getCaller());

										sendGroupChatMissedCallStanza(fromJid, toJid, session.getSid(), 
												session.getCallType(), session.getRoomId());

										log.info("Call SID {} fully processed. Notification sent to {}", session.getSid(), toJid);

										return Mono.empty();
									})
									.then();

								}))
								.then(); // Ensure we return Mono<Void>
					}))
					.then();
				})
				.then();
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

	private Mono<CallSession> getMucMissedCallSession(String mucSid) {
		String metaKey = CallSessionRedisKey.CALL_METADATA_PREFIX.format(mucSid);

		return reactiveRedisTemplate.opsForHash().entries(metaKey)
				.collectMap(
						entry -> entry.getKey().toString(), 
						entry -> entry.getValue().toString()
						)
				.flatMap(metadata -> {
					if (metadata.isEmpty()) {
						log.warn("Missed call metadata missing for SID: {}.", mucSid);
						return Mono.empty();
					}

					// Extract values (Metadata is Map<String, String>, no casting needed)
					String sid = metadata.get(CallSessionMetadata.SID.getKey());
					String toJid = metadata.get(CallSessionMetadata.TO_JID.getKey());
					String fromJid = metadata.get(CallSessionMetadata.FROM_JID.getKey());
					String tenantId = metadata.get(CallSessionMetadata.TENANT_ID.getKey());
					String groupId = metadata.get(CallSessionMetadata.GROUP_ID.getKey());
					String type = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());

					// Build the session
					CallSession session = CallSession.builder()
							.sid(sid)
							.callee(XmppUtil.getUserKey(toJid))
							.caller(XmppUtil.getUserKey(fromJid)) // Fixed: likely intended fromJid here, not metaKey
							.roomId(groupId)
							.tenantId(StringUtils.hasText(tenantId) ? Integer.parseInt(tenantId) : 0)
							.callType(type)
							.build();	

					// Use Mono.just to return the object in a flatMap
					return Mono.just(session);
				});
	}					

	/**
	 * The core processing logic for an individual expired call session.
	 * * @param sid The Session ID to process.
	 * @return Mono<Void>
	 */
	private Mono<Void> processMissedCall(String mucSid) {
		String metaKey = CallSessionRedisKey.CALL_METADATA_PREFIX.format(mucSid);

		return reactiveRedisTemplate.opsForHash().entries(metaKey)
				.collectMap(
						entry -> entry.getKey().toString(), 
						entry -> entry.getValue().toString()
						)
				.flatMap(metadata -> {
					if (metadata.isEmpty()) {
						log.warn("Missed call metadata missing for SID: {}.", mucSid);
						return Mono.empty();
					}

					String sid = (String) metadata.get(CallSessionMetadata.SID.getKey());
					String toJid = (String) metadata.get(CallSessionMetadata.TO_JID.getKey());
					String fromJid = (String) metadata.get(CallSessionMetadata.FROM_JID.getKey());
					String type = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());
					String tenantId = (String) metadata.get(CallSessionMetadata.TENANT_ID.getKey());
					String username = (String) metadata.get(CallSessionMetadata.USERNAME.getKey());
					String groupId = (String) metadata.get(CallSessionMetadata.GROUP_ID.getKey());

					int tenantIdInt = Integer.parseInt(tenantId);
					String toUserKey = XmppUtil.getUserKey(toJid);

					log.info("Processing missed call SID: {} for user: {}", sid, toUserKey);

					// --- CHANGE STARTS HERE ---
					// Wrap EVERYTHING that touches synchronous Redis/Registry into the Runnable
					return Mono.fromRunnable(() -> {
						TenantContext.setCurrentTenant(tenantIdInt);
						try {
							// Move the blocking Registry call INSIDE the protected thread
							Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
							boolean hasActiveSession = !CollectionUtils.isEmpty(userSessions) && userSessions.stream()
									.anyMatch(s -> UserState.ACTIVE == s.getState());

							sendGroupChatMissedCallStanza(fromJid, toJid, sid, type, groupId);

							// Delete MUC call session from DB
							mucCallTrackerService.remove(sid, toUserKey).subscribe();

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

		String fromRoomJid = jidUtil.getGroupBareJid(groupId) + "/" + 
				(callerMucMember.isPresent() ? callerMucMember.get().getUserKey() : "");

		String xml = String.format(
				"<message from='%s' to='%s' type='groupchat' id='%s'>" +
						"<subject>Missed %s Call</subject>" +
						"<body>Missed %s call</body>" +
						"<call-log xmlns='urn:xmpp:algomeet:calls' type='%s' status='missed' timestamp='%s' sid='%s'/>" +
						"</message>",
						fromRoomJid, toJid, id, type, type, type, Instant.now().toString(), sid
				);	

		StanzaInfo info = StanzaInfo.builder().messageId(UUID.randomUUID().toString().toLowerCase()).build();

		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
		// Insert stanza ID
		String forArchiveXml = XmppStanzaUtil.insertStanzaId(xml, ulidString, domainProperties.getDomain());
		
		xmppArchiveService.archiveEvent(forArchiveXml, info, groupId, toUserKey, 
				fromUserKey, UlidCreator.getMonotonicUlid().toLowerCase())
		.doOnSuccess(success -> {
			// Publish 
			clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.GROUPCHAT, forArchiveXml);

			// Increment MUC unread messages count 
			mucUnreadCountService.incrementForRoomMembers(groupId,
					List.of(toUserKey), 
					fromUserKey);

		})
		.doOnError(e -> log.error("MUC Archive failed: {}", e.getMessage()))
		.subscribe();	

		// Send timeout message
		String timeoutId = java.util.UUID.randomUUID().toString();
		JingleTerminationIq timeoutStanza = JingleTerminationIq.builder()
				.id(timeoutId)
				.from(fromRoomJid)
				.to(toJid)
				.sid(sid)
				.reason(JingleTerminationIq.REASON_TIMEOUT)
				.build();

		// Publish 
		clusterMessagePublisher.convertAndSendToUser(timeoutId, toUserKey, fromUserKey, ChatType.GROUPCHAT, timeoutStanza.toXml());
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
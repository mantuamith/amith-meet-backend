package com.algomeet.xmpp.chatservice.routing.call;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RSemaphoreReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.enums.CallSessionMetadata;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.CallProperties;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.MucCallTrackerService;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ============================================================================
 * MUC CALL LIFE CYCLE TRACKER
 * ============================================================================
 *
 * Purpose:
 * Monitors XMPP Jingle call signaling stanzas and manages call lifecycle state.
 *
 * This component acts as the "traffic controller" for group call sessions.
 *
 * It listens to:
 *
 * 1. session-initiate   -> Someone started a call
 * 2. session-accept     -> Someone answered
 * 3. session-terminate  -> Someone ended / rejected / canceled
 *
 * ----------------------------------------------------------------------------
 * WHY THIS CLASS EXISTS
 * ----------------------------------------------------------------------------
 *
 * Jingle signaling is real-time and temporary.
 * If a user disconnects or ignores the call, the server still needs to know:
 *
 * - Was the call missed?
 * - Was it accepted?
 * - Was it rejected?
 * - Was it canceled by caller?
 * - Was it busy?
 * - How long did the call last?
 *
 * Redis is used as temporary fast storage for ringing sessions.
 *
 * ----------------------------------------------------------------------------
 * REDIS USAGE
 * ----------------------------------------------------------------------------
 *
 * HASH:
 *   Stores metadata of active ringing call.
 *
 * ZSET:
 *   Used as delayed queue.
 *   score = timestamp when ringing timeout expires.
 *
 * Background worker can later scan expired entries and mark them missed.
 *
 * ----------------------------------------------------------------------------
 * DESIGN BENEFITS
 * ----------------------------------------------------------------------------
 *
 * ✔ Fast call timeout processing
 * ✔ Crash-safe temporary metadata
 * ✔ Prevent duplicate missed call logs
 * ✔ Supports multi-node cluster
 * ✔ Decouples signaling from persistence
 *
 * ============================================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucCallLifeCycleTracker {

	private final ReactiveStringRedisTemplate reactiveRedisTemplate;
	private final ClusterMessagePublisher reactiveClusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;
	private final MucCallTrackerService mucCallTrackerService;
	private final JidUtil jidUtil;
	private final CallProperties callProperties;
	private final RedissonReactiveClient redissonReactiveClient;
	private final DomainProperties domainProperties;

	/**
	 *
	 * Called whenever inbound call-related XMPP stanza is detected.
	 *
	 * This method classifies signaling type then delegates.
	 *
	 * @param ctx       Netty channel context
	 * @param toJid     recipient JID
	 * @param fromJid   sender JID
	 * @param xml       raw XMPP stanza
	 * @param principal authenticated session
	 * @param groupId   room/group id
	 */
	public Mono<Void> track(ChannelHandlerContext ctx,
			String toJid,
			String fromJid,
			String xml,
			XmppPrincipal principal,
			UUID groupId,
		    Integer messageRetentionDays) {

		/**
		 * Detect Jingle actions.
		 *
		 * Lightweight contains() used for speed.
		 */
		boolean isInitiate = xml.contains("session-initiate");
		boolean isAccept = xml.contains("session-accept");
		boolean isTerminate = xml.contains("session-terminate");

		/**
		 * Every call must have SID.
		 */
		String sid = XmppStanzaUtil.getAttribute(xml, "sid");		

		if (sid == null) {
			log.warn("Ignoring call stanza without SID");
			return Mono.empty();
		}

		/**
		 * Route to proper lifecycle handler.
		 */
		if (isInitiate) {
			return handleInitiate(toJid, fromJid, xml, sid, groupId, principal).then();
		} else if (isAccept) {
			return handleAccept(sid, UUID.fromString(principal.getUserKey()), principal.getSessionId()).then();
		} else if (isTerminate) {
			return handleTerminate(ctx, toJid, fromJid, xml, sid, groupId.toString(), principal, messageRetentionDays);
		}

		return Mono.empty();
	}

	/**
	 * =========================================================================
	 * CALL STARTED
	 * =========================================================================
	 *
	 * Triggered when caller starts ringing target(s).
	 *
	 * Responsibilities:
	 *
	 * 1. Store temporary metadata in Redis HASH
	 * 2. Add SID to delayed timeout queue
	 * 3. Persist tracker row for analytics/duration
	 * @return 
	 */
	private Mono<CallSession> handleInitiate(String toJid,
			String fromJid,
			String xml,
			String sid,
			UUID roomId,
			XmppPrincipal principal) {

		/**
		 * Exact future timestamp when call becomes missed.
		 */
		long executeAt =
				System.currentTimeMillis()
				+ (callProperties.getRingingTimeout().getSeconds() * 1000L);

		/**
		 * Detect media type.
		 */
		boolean isVideo =
				xml.matches("(?s).*media=['\"]video['\"].*");

		String callType = isVideo ? "video" : "audio";

		// Generate Redis MUC SID using sid and callee user key
		String mucSid = CallSessionRedisKey.getMucSid(sid, XmppUtil.getUserKey(toJid));

		/**
		 * Redis key for call metadata.
		 */
		String metadataKey = CallSessionRedisKey.CALL_METADATA_PREFIX.format(mucSid);

		Map<String, String> data = new HashMap<>();
		data.put(CallSessionMetadata.SID.getKey(), sid);
		data.put(CallSessionMetadata.TO_JID.getKey(), toJid);
		data.put(CallSessionMetadata.FROM_JID.getKey(), fromJid);
		data.put(CallSessionMetadata.CALL_TYPE.getKey(), callType);
		data.put(CallSessionMetadata.TENANT_ID.getKey(),
				principal.getTenantId().toString());
		data.put(CallSessionMetadata.GROUP_ID.getKey(), roomId.toString());
		data.put(CallSessionMetadata.USERNAME.getKey(),
				principal.getUsername());

		/**
		 * Save metadata.
		 */
		Mono<Boolean> putAllMono = reactiveRedisTemplate.opsForHash().putAll(metadataKey, data);

		/**
		 * Safety expiration.
		 */
		Mono<Boolean> expireMono = reactiveRedisTemplate.expire(metadataKey, 
				java.time.Duration.ofSeconds(callProperties.getSessionMetadataTtl().getSeconds()));

		/**
		 * Add to delayed queue.
		 *
		 * score = future timeout timestamp
		 */
		Mono<Boolean> zAddMono = reactiveRedisTemplate.opsForZSet().add(
				CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(),
				mucSid,
				(double) executeAt
		);

		/**
		 * Save persistent tracker row.
		 */
		return Mono.zip(putAllMono, expireMono, zAddMono)
				.doOnSuccess(v -> log.info("Call [{}] initiated Redis MUC SID={} timeout={}s",
						callType,
						mucSid,
						callProperties.getRingingTimeout().getSeconds()))
				.then(mucCallTrackerService.trackInitiation(
						sid,
						UUID.fromString(principal.getUserKey()),
						principal.getSessionId(),
						UUID.fromString(XmppUtil.getUserKey(toJid)),
						callType,
						roomId
				));
	}

	/**
	 * Read all call metadata in one Redis round trip.
	 */
	private Mono<Map<Object, Object>> getSessionMetadata(String sid) {
		return reactiveRedisTemplate.opsForHash()
				.entries(CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid))
				.collectMap(Map.Entry::getKey, Map.Entry::getValue);
	}

	/**
	 * =========================================================================
	 * CALL ANSWERED
	 * =========================================================================
	 *
	 * Important:
	 * Remove timeout queue immediately so background worker
	 * does NOT produce false missed-call log.
	 * @return 
	 */
	private Mono<CallSession> handleAccept(String sid,
			UUID calleeUserKey,
			String calleeSid) {

		// Generate Redis MUC SID using sid and callee user key
		String mucId = CallSessionRedisKey.getMucSid(sid, calleeUserKey.toString());

		log.info("Call accepted MUC SID={}", mucId);

		return handleResolution(mucId)
				.then(mucCallTrackerService.trackAcceptance(
						sid,
						calleeUserKey,
						calleeSid
				));
	}

	/**
	 * =========================================================================
	 * CALL TERMINATED
	 * =========================================================================
	 *
	 * Can represent many states:
	 *
	 * success      -> normal hangup after active call
	 * decline      -> recipient rejected
	 * cancel       -> caller canceled before answer
	 * busy         -> already in another call
	 * unknown      -> unexpected failure
	 * @return 
	 */
	private Mono<Void> handleTerminate(ChannelHandlerContext ctx,
			String toJid,
			String fromJid,
			String xml,
			String sid, 
			String groupId,
			XmppPrincipal principal,
			Integer messageRetentionDays) {

		return getSessionMetadata(sid)
				.flatMap(metadata -> {
					if (metadata.isEmpty()) {
						return Mono.empty();
					}

					String callType = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());

					/**
					 * NORMAL CALL END
					 */
					if (xml.contains("<success/>")) {
						return mucCallTrackerService.finalizeAndNotify(sid, principal.getSessionId(), "success", messageRetentionDays);
					}

					/**
					 * REJECTED
					 */
					else if (xml.contains("<decline/>")) {

						// Generate Redis MUC SID using sid and callee user key
						String redisMucSid = CallSessionRedisKey.getMucSid(sid, principal.getUserKey());
						
						return isCallInDelayQueue(redisMucSid)
								.flatMap(isCallInDelayQueue -> {
									if (isCallInDelayQueue) {
										/**
										 * Always cleanup first to avoid races.
										 */
										String fromRoomFullJid = jidUtil.getGroupBareJid(groupId) + "/" + XmppUtil.getUserKey(fromJid);

										return handleResolution(redisMucSid)
												.then(sendCallLog(ctx, fromRoomFullJid, fromJid, sid, "declined", "Call Declined", callType))
												.then(mucCallTrackerService.remove(sid, UUID.fromString(principal.getUserKey())));
									}
									return Mono.empty();
								});
					}

					/**
					 * CANCELED BEFORE ANSWERED
					 */
					else if (xml.contains("<cancel/>")) {
						// create from room JID
						String fromRoomFullJid = jidUtil.getGroupBareJid(groupId) + "/"	+ XmppUtil.getUserKey(fromJid);
						
						// To prevent competing threads
						// A Semaphore with 1 permit acts exactly like a non-reentrant lock
						RSemaphoreReactive semaphore = redissonReactiveClient.getSemaphore("xmpp:lock:cancel:sid:" + sid + ":user-key:" + XmppUtil.getUserKey(fromJid));
						// tryAcquire(permits, waitTime, unit)
						// permits: 1 (only one process can enter)
						// waitTime: 0 (immediate fail-fast; if the permit is taken, we discard the redundant trigger)
						return semaphore.tryAcquire(1, 0, TimeUnit.SECONDS)
								.flatMap(acquired -> {
									if (!acquired) {
										// No permit taken, return early without releasing anything
										return Mono.empty();
									}

									// Permit taken -> chain the logic and defer the release until AFTER handleCancelCall finishes
									return handleCancelCall(ctx, fromJid, sid, fromRoomFullJid, callType)
											.then() 
											.doFinally(sig -> semaphore.release(1).subscribe()); 
								});
					}

					/**
					 * BUSY
					 */
					else if (xml.contains("<busy/>")) {
						// No need to send logs to caller nor receiver

						// Generate Redis MUC SID using sid and callee user key
						String calleeUserKey = principal.getUserKey();
						String mucSid = CallSessionRedisKey.getMucSid(sid, calleeUserKey);

						return handleResolution(mucSid)
								.then(mucCallTrackerService.remove(sid, UUID.fromString(principal.getUserKey())));
					}

					/**
					 * OTHER NON-LOGGING STATES
					 */
					else if (xml.contains("<alternative-session>")
							|| xml.contains("<unsupported-transports/>")) {

						// Generate Redis MUC SID using sid and callee user key
						String calleeUserKey = principal.getUserKey();
						String redisMucSid = CallSessionRedisKey.getMucSid(sid, calleeUserKey);

						/**
						 * Always cleanup first to avoid races.
						 */
						return handleResolution(redisMucSid)
								.then(mucCallTrackerService.remove(sid, UUID.fromString(principal.getUserKey())));
					}

					/**
					 * UNKNOWN FAILURE
					 */
					else {
						log.error("Unknown error terminated the MUC call {}", xml);
						return Mono.empty();
					}
				});
	}

	private Flux<CallSession> handleCancelCall(ChannelHandlerContext ctx,
			String fromJid,
			String sid,
			String fromRoomFullJid,
			String callType) {

		// Send logs to responders
		return mucCallTrackerService.findBySid(sid)
				.flatMap(callSession -> {
					UUID calleeUserKey = callSession.getCallee();

					// Generate Redis MUC SID using sid and callee user key
					String mucSid = CallSessionRedisKey.getMucSid(sid, calleeUserKey.toString());	
					
					return isCallInDelayQueue(mucSid)
							.flatMap(isCallInDelayQueue -> {
								if (isCallInDelayQueue) {					
									/**
									 * Always cleanup first to avoid races.
									 */
									return handleResolution(mucSid)
											.then(sendCallLog(ctx, fromRoomFullJid, jidUtil.getBareJid(calleeUserKey.toString()),
													sid, "missed", "Missed Call", callType))
											.then(Mono.just(callSession));
								}
								return Mono.just(callSession);
							});
				})
				.concatWith(
						// Safely defer subsequent logic to downstream execution chains to simulate step-ordering
						Mono.defer(() -> sendCallLog(ctx, fromRoomFullJid, fromJid, sid, "canceled", "Call Canceled", callType)
								.then(mucCallTrackerService.deleteBySid(sid))
								.onErrorResume(ex -> Mono.empty()) // silent failure
								.then(Mono.empty()))
				);
	}

	/**
	 * Remove all Redis temporary state for this call.
	 *
	 * Includes:
	 * - delayed queue item
	 * - metadata hash
	 */
	private Mono<Void> handleResolution(String sid) {
		Mono<Long> removeZSet = reactiveRedisTemplate.opsForZSet().remove(CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(), sid);
		Mono<Long> deleteHash = reactiveRedisTemplate.delete(CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid));
		return Mono.when(removeZSet, deleteHash);
	}

	/**
	 * Returns true if still waiting in delayed queue.
	 */
	public Mono<Boolean> isCallInDelayQueue(String sid) {
		return reactiveRedisTemplate.opsForZSet().score(
				CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(), sid)
				.map(score -> true)
				.defaultIfEmpty(false);
	}

	/**
	 * =========================================================================
	 * SEND CALL LOG MESSAGE
	 * =========================================================================
	 *
	 * Persists call history and broadcasts to all user devices.
	 */
	private Mono<Void> sendCallLog(ChannelHandlerContext ctx,
			String fromRoomJid,
			String toJid,
			String sid,
			String status,
			String bodyText,
			String callType) {

		UUID messageId = UuidCreator.getTimeOrderedEpoch();
		String timestamp = java.time.Instant.now().toString();

		StringBuilder xml = new StringBuilder();

		xml.append("<message from='").append(fromRoomJid).append("' ")
		.append("to='").append(toJid).append("' ")
		.append("type='groupchat' ")
		.append("id='").append(messageId).append("'>")
		.append("<subject>").append(bodyText).append("</subject>")
		.append("<body>").append(bodyText).append("</body>")
		.append("<call-log xmlns='urn:xmpp:algomeet:calls' ")
		.append("type='").append(callType).append("' ")
		.append("status='").append(status).append("' ")
		.append("timestamp='").append(timestamp).append("' ")
		.append("sid='").append(sid).append("'/>")
		.append("<countable xmlns='urn:algomeet:meta:0'/>")
		.append("</message>");

		String toUserKey = XmppUtil.getUserKey(toJid);
		String fromUserKey = XmppUtil.getResourceFromRoomFullJid(fromRoomJid);
		
        UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
		// Insert stanza ID
		String forArchiveXml = XmppStanzaUtil.insertStanzaId(xml.toString(), stanzaId.toString(), domainProperties.getDomain());		
		
		/**
		 * Persist for offline retrieval.
		 */
		Mono<Void> saveOfflineMono = offlineMessageService.save(
				messageId,
				stanzaId,
				toUserKey,
				fromUserKey,
				XmppMessageType.GROUPCHAT.getXmlValue(),
				forArchiveXml
		).then();

		/**
		 * Push to cluster for all online devices.
		 */
		Mono<Void> clusterPublishMono = reactiveClusterMessagePublisher.convertAndSendToUser(
				messageId.toString(),
				toUserKey,
				fromUserKey,
				ChatType.CHAT,
				forArchiveXml
		).then();

		return Mono.when(saveOfflineMono, clusterPublishMono)
				.doOnSuccess(v -> log.debug("Published {} call log SID={}", status, sid));
	}
}
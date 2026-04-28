package com.algomeet.xmpp.chatservice.routing.call;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RSemaphoreReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.enums.CallSessionMetadata;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.CallProperties;
import com.algomeet.xmpp.chatservice.service.MucCallTrackerService;
import com.algomeet.xmpp.chatservice.service.MucUnreadCountService;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

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

	private final StringRedisTemplate redisTemplate;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;
	private final MucCallTrackerService mucCallTrackerService;
	private final JidUtil jidUtil;
	private final MucUnreadCountService mucUnreadCountService;
	private final CallProperties callProperties;
	private final RedissonReactiveClient redissonReactiveClient;

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
	public void track(ChannelHandlerContext ctx,
			String toJid,
			String fromJid,
			String xml,
			XmppPrincipal principal,
			String groupId) {

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
			return;
		}

		/**
		 * Route to proper lifecycle handler.
		 */
		if (isInitiate) {
			handleInitiate(toJid, fromJid, xml, sid, groupId, principal);

		} else if (isAccept) {
			handleAccept(sid,
					principal.getUserKey(),
					principal.getSessionId());

		} else if (isTerminate) {
			handleTerminate(ctx,
					toJid,
					fromJid,
					xml,
					sid,
					groupId,
					principal);
		}
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
	 */
	private void handleInitiate(String toJid,
			String fromJid,
			String xml,
			String sid,
			String roomId,
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
		data.put(CallSessionMetadata.GROUP_ID.getKey(), roomId);
		data.put(CallSessionMetadata.USERNAME.getKey(),
				principal.getUsername());

		/**
		 * Save metadata.
		 */
		redisTemplate.opsForHash().putAll(metadataKey, data);

		/**
		 * Safety expiration.
		 */
		redisTemplate.expire(metadataKey, callProperties.getSessionMetadataTtl().getSeconds(), 
				TimeUnit.SECONDS);

		/**
		 * Add to delayed queue.
		 *
		 * score = future timeout timestamp
		 */
		redisTemplate.opsForZSet().add(CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(),
				mucSid,
				executeAt
				);

		log.info("Call [{}] initiated Redis MUC SID={} timeout={}s",
				callType,
				mucSid,
				callProperties.getRingingTimeout().getSeconds());

		/**
		 * Save persistent tracker row.
		 */
		mucCallTrackerService.trackInitiation(
				sid,
				principal.getUserKey(),
				principal.getSessionId(),
				XmppUtil.getUserKey(toJid),
				callType,
				roomId
				).subscribe();
	}

	/**
	 * Read all call metadata in one Redis round trip.
	 */
	private Map<Object, Object> getSessionMetadata(String sid) {
		return redisTemplate.opsForHash()
				.entries(CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid));
	}

	/**
	 * =========================================================================
	 * CALL ANSWERED
	 * =========================================================================
	 *
	 * Important:
	 * Remove timeout queue immediately so background worker
	 * does NOT produce false missed-call log.
	 */
	private void handleAccept(String sid,
			String calleeUserKey,
			String calleeSid) {

		// Generate Redis MUC SID using sid and callee user key
		String mucId = CallSessionRedisKey.getMucSid(sid, calleeUserKey);

		log.info("Call accepted MUC SID={}", mucId);

		handleResolution(mucId);

		mucCallTrackerService.trackAcceptance(
				sid,
				calleeUserKey,
				calleeSid
				).subscribe();
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
	 */
	private void handleTerminate(ChannelHandlerContext ctx,
			String toJid,
			String fromJid,
			String xml,
			String sid,
			String groupId,
			XmppPrincipal principal) {

		Map<Object, Object> metadata = getSessionMetadata(sid);

		if (metadata == null) {
			return;
		}

		String callType = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());

		/**
		 * NORMAL CALL END
		 */
		if (xml.contains("<success/>")) {
			mucCallTrackerService.finalizeAndNotify(sid, principal.getSessionId(), "success")
			.subscribe();
		}

		/**
		 * REJECTED
		 */
		else if (xml.contains("<decline/>")) {

			// Generate Redis MUC SID using sid and callee user key
			String redisMucSid = CallSessionRedisKey.getMucSid(sid, principal.getUserKey());
			boolean isCallInDelayQueue = isCallInDelayQueue(redisMucSid);

			if(isCallInDelayQueue) {
				/**
				 * Always cleanup first to avoid races.
				 */
				handleResolution(redisMucSid);

				// create from room JID
				String fromRoomFullJid = jidUtil.getGroupBareJid(groupId) + "/"	+ XmppUtil.getUserKey(fromJid);

				// Send to responder only
				sendCallLog(ctx, fromRoomFullJid, fromJid,
						sid, "declined",
						"Call Declined", callType);

				// Delete MUC call session 
				mucCallTrackerService.remove(sid, principal.getUserKey()).subscribe();	
			}	
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
			semaphore.tryAcquire(1, 0, TimeUnit.SECONDS) // Try to get 1 permit immediately
			.flatMap(acquired -> {
				if (!acquired) {
					// If lock is held, another process is already finalizing this SID.
					return Mono.empty();
				}

				return handleCancelCall(ctx,
						fromJid,
						sid,
						fromRoomFullJid,
						callType)
						.then();
			})
			// Use finalize to ensure unlock happens regardless of success/error/empty
			// Use doFinally to ensure the permit is ALWAYS released
			.doFinally(sig -> semaphore.release(1).subscribe()) 
			.then();
		}

		/**
		 * BUSY
		 */
		else if (xml.contains("<busy/>")) {
			// No need to send logs to caller nor receiver

			// Generate Redis MUC SID using sid and callee user key
			String calleeUserKey = principal.getUserKey();
			String mucSid = CallSessionRedisKey.getMucSid(sid, calleeUserKey);

			handleResolution(mucSid);

			// remove from db
			mucCallTrackerService.remove(sid, principal.getUserKey()).subscribe();
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
			handleResolution(redisMucSid);

			// Remove call session from database
			mucCallTrackerService.remove(sid, principal.getUserKey()).subscribe();
		}

		/**
		 * UNKNOWN FAILURE
		 */
		else {

			log.error("Unknown error terminated the MUC call {}", xml);
		}
	}

	private Flux<CallSession> handleCancelCall(ChannelHandlerContext ctx,
			String fromJid,
			String sid,
			String fromRoomFullJid,
			String callType) {

		// Send logs to responders
		return mucCallTrackerService.findBySid(sid)
				.doOnEach(callSession -> {
					String calleeUserKey = callSession.get().getCallee();

					// Generate Redis MUC SID using sid and callee user key
					String mucSid = CallSessionRedisKey.getMucSid(sid, calleeUserKey);	
					boolean isCallInDelayQueue = isCallInDelayQueue(mucSid);

					if(isCallInDelayQueue) {					
						/**
						 * Always cleanup first to avoid races.
						 */
						handleResolution(mucSid);

						// Send to responder
						sendCallLog(ctx, fromRoomFullJid, jidUtil.getBareJid(calleeUserKey),
								sid, "missed",
								"Missed Call", callType);
					}
				})
				.doFinally(signal -> {
					// Send call log to caller
					sendCallLog(ctx, fromRoomFullJid, fromJid,
							sid, "canceled",
							"Call Canceled", callType);

					try {
						// Delete muc call session records
						mucCallTrackerService.deleteBySid(sid).subscribe();
					} catch(Exception ex) {
						// silent
					}

				});		
	}

	/**
	 * Remove all Redis temporary state for this call.
	 *
	 * Includes:
	 * - delayed queue item
	 * - metadata hash
	 */
	private void handleResolution(String sid) {

		redisTemplate.opsForZSet().remove(CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(), sid);
		redisTemplate.delete(CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid));
	}

	/**
	 * Returns true if still waiting in delayed queue.
	 */
	public boolean isCallInDelayQueue(String sid) {

		Double score = redisTemplate.opsForZSet().score(
				CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(),	sid);

		return score != null;
	}

	/**
	 * =========================================================================
	 * SEND CALL LOG MESSAGE
	 * =========================================================================
	 *
	 * Persists call history and broadcasts to all user devices.
	 */
	private void sendCallLog(ChannelHandlerContext ctx,
			String fromRoomJid,
			String toJid,
			String sid,
			String status,
			String bodyText,
			String callType) {

		String messageId = java.util.UUID.randomUUID().toString();
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
		.append("</message>");

		String toUserKey = XmppUtil.getUserKey(toJid);
		String roomId = XmppUtil.getRoomId(fromRoomJid);
		String fromUserKey = XmppUtil.getResourceFromRoomFullJid(fromRoomJid);

		/**
		 * Persist for offline retrieval.
		 */
		offlineMessageService.save(
				messageId,
				toUserKey,
				fromUserKey,
				XmppMessageType.GROUPCHAT.getXmlValue(),
				xml.toString()
				)
		.doOnSuccess(success -> {
			// Increment MUC unread messages count 
			mucUnreadCountService.incrementForRoomMembers(roomId,
					List.of(toUserKey), 
					fromUserKey)
			.subscribe();
		})
		.subscribe();

		/**
		 * Push to cluster for all online devices.
		 */
		clusterMessagePublisher.convertAndSendToUser(
				messageId,
				toUserKey,
				fromUserKey,
				ChatType.CHAT,
				xml.toString()
				);

		log.debug("Published {} call log SID={}", status, sid);
	}
}
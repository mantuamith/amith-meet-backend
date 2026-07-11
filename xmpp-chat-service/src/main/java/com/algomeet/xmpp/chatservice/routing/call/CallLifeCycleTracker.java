package com.algomeet.xmpp.chatservice.routing.call;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.CallTrackerService;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.service.UnreadCountService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <p><strong>Call Lifecycle Tracker & Redis Orchestrator</strong></p>
 * * <p>Responsible for monitoring Jingle (XEP-0166) signaling to maintain call state in Redis.
 * It manages the "Ringing" window by scheduling delayed tasks and ensures that 
 * 'Accepted' or 'Terminated' calls do not trigger accidental 'Missed Call' logs.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallLifeCycleTracker {
	private final StringRedisTemplate redisTemplate;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;
	private final CallTrackerService callTrackerService;
	private final UnreadCountService unreadCountService;
	private final CallProperties callProperties;
	private final DomainProperties domainProperties;

	/**
	 * Entry point for analyzing incoming XMPP stanzas for Jingle signaling actions.
	 * Now safely returns a unified Mono pipeline context without dangling subscriptions.
	 */
	public Mono<Void> track(ChannelHandlerContext ctx, String toJid, String fromJid, String xml, XmppPrincipal principal) {
		// Detect specific Jingle actions defined in XEP-0166
		boolean isInitiate = xml.contains("session-initiate");
		boolean isAccept = xml.contains("session-accept");
		boolean isTerminate = xml.contains("session-terminate");

		String sid = XmppStanzaUtil.getAttribute(xml, "sid");
		if (sid == null) {
			return Mono.empty();
		}

		if (isInitiate) {
			return handleInitiate(toJid, fromJid, xml, sid, principal)
					.then();
		} else if (isAccept) {
			return handleAccept(sid, UUID.fromString(principal.getUserKey()), principal.getSessionId())
					.then();
		} else if (isTerminate) {
			return handleTerminate(ctx, toJid, fromJid, xml, sid, principal);
		}

		return Mono.empty();
	}

	/**
	 * Handles 'session-initiate'. Registers the call metadata and starts the 
	 * countdown timer in Redis for the "Missed Call" worker.
	 * @return 
	 */
	private Mono<CallSession> handleInitiate(String toJid, String fromJid, String xml, String sid, XmppPrincipal principal) {
		// Calculate the exact epoch millisecond when the call should be considered "Missed"
		long executeAt = System.currentTimeMillis() + (callProperties.getRingingTimeout().getSeconds() * 1000);

		// Detect media type (Video/Audio) using quote-agnostic regex per XEP-0167
		boolean isVideo = xml.matches("(?s).*media=['\"]video['\"].*");
		String callType = isVideo ? "video" : "audio";

		// 1. Store call metadata in a Redis Hash. 
		// This is the source of truth for the background worker if the call times out.
		String metaKey = CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid);
		Map<String, String> data = new HashMap<>();
		data.put(CallSessionMetadata.TO_JID.getKey(), toJid);
		data.put(CallSessionMetadata.FROM_JID.getKey(), fromJid);
		data.put(CallSessionMetadata.CALL_TYPE.getKey(), callType);
		data.put(CallSessionMetadata.TENANT_ID.getKey(), principal.getTenantId().toString()); 
		data.put(CallSessionMetadata.USERNAME.getKey(), principal.getUsername()); 

		redisTemplate.opsForHash().putAll(metaKey, data);

		// Apply a safety TTL to avoid memory leaks if the server crashes before processing
		redisTemplate.expire(metaKey, callProperties.getSessionMetadataTtl().getSeconds(), TimeUnit.SECONDS); 

		// 2. Add the SID to the ZSET (Delayed Queue). 
		// The 'score' is the expiration time; the worker polls for scores <= current time.
		redisTemplate.opsForZSet().add(CallSessionRedisKey.DIRECT_CALL_TIMEOUT_QUEUE.getVal(), sid, executeAt);
		log.info("Call [{}] initiated. SID: {}. Timeout scheduled in {}s", callType, sid, 
				callProperties.getRingingTimeout().getSeconds());
		
		// Track call initiation for duration calculation
		return callTrackerService.trackInitiation(sid, UUID.fromString(principal.getUserKey()), principal.getSessionId(), 
				UUID.fromString(XmppUtil.getUserKey(toJid)), callType);
	}

	/**
	 * Fetches multiple metadata fields in a single Redis round-trip.
	 * Optimized to reduce network latency and command overhead.
	 */
	private Map<Object, Object> getSessionMetadata(String sid) {
	    String metaKey = CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid);
	    // Fetch the entire hash at once
	    return redisTemplate.opsForHash().entries(metaKey);
	}

	/**
	 * Handles 'session-accept'. 
	 * Crucial: We must remove the SID from Redis immediately so the MissedCallScheduler 
	 * doesn't send a "Missed Call" notification for an active conversation.
	 * @return 
	 */
	private Mono<CallSession> handleAccept(String sid, UUID calleeUserKey, String calleeSid) {
		log.info("Call accepted for SID: {}. Killing timeout timer.", sid);
		handleResolution(sid);
		
		// Track call acceptance for duration calculation
		return callTrackerService.trackAcceptance(sid, calleeUserKey, calleeSid);
		
	}

	/**
	 * Handles 'session-terminate'. 
	 * Identifies why the call ended (Rejected vs Canceled) and generates appropriate logs.
	 * @return 
	 */
	private Mono<Void> handleTerminate(ChannelHandlerContext ctx, String toJid, String fromJid, String xml, String sid, XmppPrincipal principal) {
		// 1. Single round-trip to get all data
	    Map<Object, Object> metadata = getSessionMetadata(sid);
	    if (metadata == null) {
	    	return Mono.empty();
	    }
	    	    
		String callType = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());

		// Check if call still in delay queue
		boolean isCallInDelayQueue = isCallInDelayQueue(sid);
		
				
		// Kill the background timer first to prevent race conditions
		handleResolution(sid);

		// Case: User pressed the "Red Button" to decline an incoming call
		if (xml.contains("<success/>")) {			
			
			// Calculate and send call logs
			return callTrackerService.finalizeAndNotify(sid, principal.getSessionId(), "success");
		}
		else if (xml.contains("<decline/>")) {
			
			// 1. To Initiator: "The other person rejected your call"
			return sendCallLog(ctx, fromJid, toJid, sid, "rejected", "Call Declined", callType)
					// 2. To Responder (Self): "You rejected this call"
					.then(sendCallLog(ctx, toJid, fromJid, sid, "declined", "Call Declined", callType))

					// 3. Remove call session for declined call
					.then(Mono.defer(() -> callTrackerService.remove(sid)));
		} 		
		// Case: Caller hung up before the recipient answered
		else if (xml.contains("<cancel/>")) {
			// 1. To Initiator (Self): "You canceled the call attempt"
			return sendCallLog(ctx, toJid, fromJid, sid, "canceled", "Call Canceled", callType)

			// 2. To Responder: "You missed an incoming call"
			.then(sendCallLog(ctx, fromJid, toJid, sid, "missed", "Missed Call", callType))

			// Remove call session for canceled call
			.then(callTrackerService.remove(sid));			
		}	
		else if (xml.contains("<busy/>")) {
			// 1. To Initiator: "Busy"
			return sendCallLog(ctx, toJid, fromJid, sid, "busy", "Line Busy", callType)

			// Remove call session for busy call
			.then(callTrackerService.remove(sid));	
		} else if (xml.contains("<alternative-session>")) {
			// No logs
			// Remove call session for busy call
			return callTrackerService.remove(sid);	
		} else if (xml.contains("<unsupported-transports/>")) {
			// No logs
			log.error("unsupported-transports error during call initiation payload");
			// Remove call session for busy call
			return callTrackerService.remove(sid);			
		} else {
			
			if (isCallInDelayQueue) {
				log.error("Unknown error during call initiation payload {} ", xml);
				// 1. To Initiator (Self): "You missed an incoming call"
				return sendCallLog(ctx, toJid, fromJid, sid, "unknown", "Unknown Error", callType)

				// 2. To Responder: "You missed an incoming call"
				.then(sendCallLog(ctx, fromJid, toJid, sid, "missed", "Unknown Error", callType))
				
				// Remove call session for busy call
				.then(callTrackerService.remove(sid));				
			} else {
				log.error("Unknown error terminates the call {} ", xml);
				// Calculate and send call logs
				return callTrackerService.finalizeAndNotify(sid, principal.getSessionId(), "success");			
			}			
		}
	}

	/**
	 * Atomically clears all Redis artifacts related to a call session.
	 * Called when a call is successfully resolved (Accepted, Rejected, or Canceled).
	 */
	private void handleResolution(String sid) {
		redisTemplate.opsForZSet().remove(CallSessionRedisKey.DIRECT_CALL_TIMEOUT_QUEUE.getVal(), sid);
		redisTemplate.delete(CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid));
	}
	
	/**
	 * Checks if the call session is still present in the delayed queue.
	 * Returns true if the sid exists, false otherwise.
	 */
	public boolean isCallInDelayQueue(String sid) {
	    // .score() returns Double (the score) if present, or null if not present
	    Double score = redisTemplate.opsForZSet().score(CallSessionRedisKey.DIRECT_CALL_TIMEOUT_QUEUE.getVal(), sid);
	    return score != null;
	}

	/**
	 * Constructs the XMPP 'headline' message and persists it to the Offline store/Cluster.
	 * @return 
	 */
	private Mono<Void> sendCallLog(ChannelHandlerContext ctx, String fromJid, String toJid, 
	        String sid, String status, String bodyText, String callType) {

	    UUID messageId = UuidCreator.getTimeOrderedEpoch();
	    String timestamp = java.time.Instant.now().toString();

	    // Building XEP-compliant message with custom AlgoMeet call-log namespace
	    StringBuilder xml = new StringBuilder();
	    xml.append("<message from='").append(fromJid).append("' ")
	       .append("to='").append(toJid).append("' ")
	       .append("type='chat' ")
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
	    String fromUserKey = XmppUtil.getUserKey(fromJid);

	    UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
	    // Insert stanza ID
	    String forArchiveXml = XmppStanzaUtil.insertStanzaId(xml.toString(), stanzaId.toString(), domainProperties.getDomain());
	    
	    // Pull the active connection principal context 
	    XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();

	    // 1. Start with the database persistence layer
	    return offlineMessageService.save(messageId, stanzaId, toUserKey, fromUserKey, XmppMessageType.CHAT.getXmlValue(), forArchiveXml)
	            // 2. Smoothly switch processing to handle the side-effect tasks once save resolves successfully
	            .flatMap(saved -> {
	                log.debug("Successfully saved call log to database. Incrementing unread count.");
	                return unreadCountService.incrementUnreadCount(fromUserKey, toUserKey);
	            })
	            // 3. Shift database/counting disk operations away from Netty's selector thread pool
	            .subscribeOn(Schedulers.boundedElastic())
	            .doOnError(e -> log.error("Storage/Increment failure during call log handling for SID {}: {}", sid, e.getMessage()))
	            // 4. Chain execution downstream to fire the cluster broadcast over Redis/WebSockets
	            .then(Mono.defer(() -> {
	                log.debug("Published {} call log across cluster for SID: {}", status, sid);
	                
	                // Matches the method signature: (id, to, from, chatType, isAllowEcho, shouldCarbon, isAckStanza, payload, principal)
	                return clusterMessagePublisher.convertAndSendToUser(
	                    messageId.toString(), toUserKey, fromUserKey, ChatType.CHAT, 
	                    false, true, false, forArchiveXml, principal
	                );
	            }));
	}
}
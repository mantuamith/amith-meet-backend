package com.algomeet.xmpp.chatservice.routing.call;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.CallSessionMetadata;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.service.CallTrackerService;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.service.UnreadCountService;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

	@Value("${call.session-metadata-ttl-minutes:10}")
	private Integer callSessionMetadataTtlMinutes;

	@Value("${call.ringing-timeout-seconds:30}")
	private Integer callRingingTimeoutSeconds;

	/**
	 * Regex pattern to extract the Session ID (sid) from Jingle elements.
	 * Compliant with XEP-0166 attribute quoting (supports both ' and ").
	 */
	private static final Pattern SID_PATTERN = Pattern.compile("sid=['\"]([^'\"]+)['\"]");

	/**
	 * Entry point for analyzing incoming XMPP stanzas for Jingle signaling actions.
	 */
	public void track(ChannelHandlerContext ctx, String toJid, String fromJid, String xml, XmppPrincipal principal) {
		// Detect specific Jingle actions defined in XEP-0166
		boolean isInitiate = xml.contains("session-initiate");
		boolean isAccept = xml.contains("session-accept");
		boolean isTerminate = xml.contains("session-terminate");

		String sid = extractSid(xml);
		if (sid == null) return;

		if (isInitiate) {
			handleInitiate(toJid, fromJid, xml, sid, principal);
		} else if (isAccept) {
			handleAccept(sid, principal.getUserKey(), principal.getSessionId());
		} else if (isTerminate) {
			handleTerminate(ctx, toJid, fromJid, xml, sid, principal);
		}
	}

	/**
	 * Handles 'session-initiate'. Registers the call metadata and starts the 
	 * countdown timer in Redis for the "Missed Call" worker.
	 */
	private void handleInitiate(String toJid, String fromJid, String xml, String sid, XmppPrincipal principal) {
		// Calculate the exact epoch millisecond when the call should be considered "Missed"
		long executeAt = System.currentTimeMillis() + (callRingingTimeoutSeconds * 1000);

		// Detect media type (Video/Audio) using quote-agnostic regex per XEP-0167
		boolean isVideo = xml.matches("(?s).*media=['\"]video['\"].*");
		String callType = isVideo ? "Video" : "Audio";

		// 1. Store call metadata in a Redis Hash. 
		// This is the source of truth for the background worker if the call times out.
		String metaKey = CallSessionRedisKey.CALL_METADATA_PREFIX.format(sid);
		Map<String, String> data = new HashMap<>();
		data.put(CallSessionMetadata.TO.getKey(), toJid);
		data.put(CallSessionMetadata.FROM.getKey(), fromJid);
		data.put(CallSessionMetadata.CALL_TYPE.getKey(), callType);
		data.put(CallSessionMetadata.TENANT_ID.getKey(), principal.getTenantId().toString()); 
		data.put(CallSessionMetadata.USERNAME.getKey(), principal.getUsername()); 

		redisTemplate.opsForHash().putAll(metaKey, data);

		// Apply a safety TTL to avoid memory leaks if the server crashes before processing
		redisTemplate.expire(metaKey, callSessionMetadataTtlMinutes, TimeUnit.MINUTES); 

		// 2. Add the SID to the ZSET (Delayed Queue). 
		// The 'score' is the expiration time; the worker polls for scores <= current time.
		redisTemplate.opsForZSet().add(CallSessionRedisKey.DIRECT_CALL_TIMEOUT_QUEUE.getVal(), sid, executeAt);
		log.info("Call [{}] initiated. SID: {}. Timeout scheduled in {}s", callType, sid, callRingingTimeoutSeconds);
		
		// Track call initiation for duration calculation
		callTrackerService.trackInitiation(sid, principal.getUserKey(), principal.getSessionId(), XmppUtil.getUserKey(toJid), callType)
		.subscribe();
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
	 */
	private void handleAccept(String sid, String calleeUserKey, String calleeSid) {
		log.info("Call accepted for SID: {}. Killing timeout timer.", sid);
		handleResolution(sid);
		
		// Track call acceptance for duration calculation
		callTrackerService.trackAcceptance(sid, calleeUserKey, calleeSid).subscribe();
	}

	/**
	 * Handles 'session-terminate'. 
	 * Identifies why the call ended (Rejected vs Canceled) and generates appropriate logs.
	 */
	private void handleTerminate(ChannelHandlerContext ctx, String toJid, String fromJid, String xml, String sid, XmppPrincipal principal) {
		// 1. Single round-trip to get all data
	    Map<Object, Object> metadata = getSessionMetadata(sid);
	    if (metadata == null) {
	    	return;
	    }
	    	    
		String callType = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());

		// Check if call still in delay queue
		boolean isCallInDelayQueue = isCallInDelayQueue(sid);
		
				
		// Kill the background timer first to prevent race conditions
		handleResolution(sid);

		// Case: User pressed the "Red Button" to decline an incoming call
		if (xml.contains("<success/>")) {			
			
			// Calculate and send call logs
			callTrackerService.finalizeAndNotify(sid, principal.getSessionId(), "success").subscribe();
		}
		else if (xml.contains("<decline/>")) {
			// 1. To Initiator: "The other person rejected your call"
		    sendCallLog(ctx, fromJid, toJid, sid, "rejected", "Call Declined", callType);
		    
		    // 2. To Responder (Self): "You rejected this call"
		    sendCallLog(ctx, toJid, fromJid, sid, "declined", "Call Declined", callType);		
		    
		    // Remove call session for declined call
			callTrackerService.remove(sid).subscribe();
		} 		
		// Case: Caller hung up before the recipient answered
		else if (xml.contains("<cancel/>")) {
			// 1. To Initiator (Self): "You canceled the call attempt"
			sendCallLog(ctx, toJid, fromJid, sid, "canceled", "Call Canceled", callType);

			// 2. To Responder: "You missed an incoming call"
			sendCallLog(ctx, fromJid, toJid, sid, "missed", "Missed Call", callType);

			// Remove call session for canceled call
			callTrackerService.remove(sid);			
		}	
		else if (xml.contains("<busy/>")) {
			// 1. To Initiator: "Busy"
			sendCallLog(ctx, toJid, fromJid, sid, "busy", "Line Busy", callType);

			// Remove call session for busy call
			callTrackerService.remove(sid);
		} else if (xml.contains("<alternative-session>")) {
			// No logs
			// Remove call session for busy call
			callTrackerService.remove(sid);
		} else if (xml.contains("<unsupported-transports/>")) {
			// No logs
			// Remove call session for busy call
			callTrackerService.remove(sid).subscribe();
			
			log.error("unsupported-transports error during call initiation payload");
		} else {
			
			if (isCallInDelayQueue) {
				// 1. To Initiator (Self): "You missed an incoming call"
				sendCallLog(ctx, toJid, fromJid, sid, "unknown", "Unknown Error", callType);

				// 2. To Responder: "You missed an incoming call"
				sendCallLog(ctx, fromJid, toJid, sid, "missed", "Unknown Error", callType);

				// Remove call session for busy call
				callTrackerService.remove(sid).subscribe();
				
				log.error("Unknown error during call initiation payload {} ", xml);
			} else {
				
				// Calculate and send call logs
				callTrackerService.finalizeAndNotify(sid, principal.getSessionId(), "success").subscribe();
				
				log.error("Unknown error terminates the call {} ", xml);
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

	private String extractSid(String xml) {
		Matcher matcher = SID_PATTERN.matcher(xml);
		return matcher.find() ? matcher.group(1) : null;
	}

	/**
	 * Constructs the XMPP 'headline' message and persists it to the Offline store/Cluster.
	 */
	private void sendCallLog(ChannelHandlerContext ctx, String fromJid, String toJid, 
			String sid, String status, String bodyText, String callType ) {

		String messageId = java.util.UUID.randomUUID().toString();
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
		.append("</message>");

		String toUserKey = XmppUtil.getUserKey(toJid);
		String fromUserKey = XmppUtil.getUserKey(fromJid);

		// Persist to MongoDB for offline retrieval
		offlineMessageService.save(messageId, toUserKey, fromUserKey, XmppMessageType.CHAT.getXmlValue(), xml.toString())
		.doOnSuccess(saved -> {
			// Increment user unread message
			unreadCountService.incrementUnreadCount(fromUserKey, toUserKey);
		})
		.doOnError(e -> log.error("Storage failure during saving of call logs {}: {}", xml.toString(), e.getMessage()))
		.subscribe();

		// Broadcast to cluster to ensure all logged-in devices of the user receive the log
		clusterMessagePublisher.convertAndSendToUser(messageId, toUserKey, fromUserKey, ChatType.CHAT, xml.toString());

		log.debug("Published {} call log for SID: {}", status, sid);
	}	
}
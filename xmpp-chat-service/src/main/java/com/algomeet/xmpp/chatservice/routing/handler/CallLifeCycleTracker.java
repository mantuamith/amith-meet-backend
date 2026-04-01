package com.algomeet.xmpp.chatservice.routing.handler;

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
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p><strong>Call Lifecycle Tracker & Redis Orchestrator</strong></p>
 * * <p>Responsible for monitoring Jingle (XEP-0166) signaling to maintain call state in Redis.
 * It manages the "Ringing" window by scheduling delayed tasks and ensures that 
 * 'Accepted' or 'Terminated' calls do not trigger accidental 'Missed Call' logs.</p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class CallLifeCycleTracker {
	private final StringRedisTemplate redisTemplate;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;

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
	public void track(ChannelHandlerContext ctx, String to, String from, String xml, XmppPrincipal principal) {
		// Detect specific Jingle actions defined in XEP-0166
		boolean isInitiate = xml.contains("urn:xmpp:jingle:1") && xml.contains("session-initiate");
		boolean isAccept = xml.contains("urn:xmpp:jingle:1") && xml.contains("session-accept");
		boolean isTerminate = xml.contains("urn:xmpp:jingle:1") && xml.contains("session-terminate");

		String sid = extractSid(xml);
		if (sid == null) return;

		if (isInitiate) {
			handleInitiate(to, from, xml, sid, principal.getTenantId());
		} else if (isAccept) {
			handleAccept(sid);
		} else if (isTerminate) {
			handleTerminate(ctx, to, from, xml, sid);
		}
	}

	/**
	 * Handles 'session-initiate'. Registers the call metadata and starts the 
	 * countdown timer in Redis for the "Missed Call" worker.
	 */
	private void handleInitiate(String to, String from, String xml, String sid, Integer tenantId) {
		// Calculate the exact epoch millisecond when the call should be considered "Missed"
		long executeAt = System.currentTimeMillis() + (callRingingTimeoutSeconds * 1000);

		// Detect media type (Video/Audio) using quote-agnostic regex per XEP-0167
		boolean isVideo = xml.matches("(?s).*media=['\"]video['\"].*");
		String callType = isVideo ? "Video" : "Audio";

		// 1. Store call metadata in a Redis Hash. 
		// This is the source of truth for the background worker if the call times out.
		String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);
		Map<String, String> data = new HashMap<>();
		data.put(CallSessionMetadata.TO.getKey(), to);
		data.put(CallSessionMetadata.FROM.getKey(), from);
		data.put(CallSessionMetadata.CALL_TYPE.getKey(), callType);
		data.put(CallSessionMetadata.TENANT_ID.getKey(), tenantId.toString()); 

		redisTemplate.opsForHash().putAll(metaKey, data);

		// Apply a safety TTL to avoid memory leaks if the server crashes before processing
		redisTemplate.expire(metaKey, callSessionMetadataTtlMinutes, TimeUnit.MINUTES); 

		// 2. Add the SID to the ZSET (Delayed Queue). 
		// The 'score' is the expiration time; the worker polls for scores <= current time.
		redisTemplate.opsForZSet().add(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid, executeAt);
		log.info("Call [{}] initiated. SID: {}. Timeout scheduled in {}s", callType, sid, callRingingTimeoutSeconds);
	}

	private String getCallType(String sid) {
		String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);
		return (String) redisTemplate.opsForHash().get(metaKey, CallSessionMetadata.CALL_TYPE.getKey());
	}

	/**
	 * Handles 'session-accept'. 
	 * Crucial: We must remove the SID from Redis immediately so the MissedCallScheduler 
	 * doesn't send a "Missed Call" notification for an active conversation.
	 */
	private void handleAccept(String sid) {
		log.info("Call accepted for SID: {}. Killing timeout timer.", sid);
		handleResolution(sid);
	}

	/**
	 * Handles 'session-terminate'. 
	 * Identifies why the call ended (Rejected vs Canceled) and generates appropriate logs.
	 */
	private void handleTerminate(ChannelHandlerContext ctx, String to, String from, String xml, String sid) {
		String callType = getCallType(sid);

		// Kill the background timer first to prevent race conditions
		handleResolution(sid);

		// Case: User pressed the "Red Button" to decline an incoming call
		if (xml.contains("<decline/>")) {
			sendCallLog(ctx, from, to, sid, "rejected", "Call Declined", callType);
		} 
		// Case: Caller hung up before the recipient answered
		else if (xml.contains("<cancel/>")) {
			sendCallLog(ctx, from, to, sid, "canceled", "Missed Call", callType);
		}
	}

	/**
	 * Atomically clears all Redis artifacts related to a call session.
	 * Called when a call is successfully resolved (Accepted, Rejected, or Canceled).
	 */
	private void handleResolution(String sid) {
		redisTemplate.opsForZSet().remove(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid);
		redisTemplate.delete(CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid));
	}

	private String extractSid(String xml) {
		Matcher matcher = SID_PATTERN.matcher(xml);
		return matcher.find() ? matcher.group(1) : null;
	}

	/**
	 * Constructs the XMPP 'headline' message and persists it to the Offline store/Cluster.
	 */
	private void sendCallLog(ChannelHandlerContext ctx, String from, String to, 
			String sid, String status, String bodyText, String callType ) {

		String messageId = java.util.UUID.randomUUID().toString();
		String timestamp = java.time.Instant.now().toString();

		// Building XEP-compliant message with custom AlgoMeet call-log namespace
		StringBuilder xml = new StringBuilder();
		xml.append("<message from='").append(from).append("' ")
		.append("to='").append(to).append("' ")
		.append("type='headline' ")
		.append("id='").append(messageId).append("'>")
		.append("<subject>").append(bodyText).append("</subject>")
		.append("<body>").append(bodyText).append("</body>")
		.append("<call-log xmlns='urn:xmpp:algomeet:calls' ")
		.append("type='").append(callType).append("' ")
		.append("status='").append(status).append("' ")
		.append("timestamp='").append(timestamp).append("' ")
		.append("sid='").append(sid).append("'/>")
		.append("</message>");

		String toUserKey = XmppUtil.getUserKey(to);
		String fromUserKey = XmppUtil.getUserKey(from);

		// Persist to MongoDB for offline retrieval
		offlineMessageService.save(messageId, toUserKey, fromUserKey, XmppMessageType.HEADLINE.getXmlValue(), xml.toString())
		.doOnError(e -> log.error("Storage failure for message {}: {}", messageId, e.getMessage()))
		.subscribe();

		// Broadcast to cluster to ensure all logged-in devices of the user receive the log
		clusterMessagePublisher.convertAndSendToUser(messageId, toUserKey, fromUserKey, ChatType.CHAT, xml.toString());

		log.debug("Published {} call log for SID: {}", status, sid);
	}
}
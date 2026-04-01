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

	// Regex to extract sid from jingle element: sid='...' or sid="..."
	private static final Pattern SID_PATTERN = Pattern.compile("sid=['\"]([^'\"]+)['\"]");

	public void track(ChannelHandlerContext ctx, String to, String from, String xml, XmppPrincipal principal) {
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

	private void handleInitiate(String to, String from, String xml, String sid, Integer tenantId) {
		long executeAt = System.currentTimeMillis() + (callRingingTimeoutSeconds * 1000);

		// Detect media type using quote-agnostic regex (XEP-0167) (handles 'video' or "video")
		boolean isVideo = xml.matches("(?s).*media=['\"]video['\"].*");
		String callType = isVideo ? "Video" : "Audio";

		// 1. Store the call metadata in a Hash for the worker to use later
		// We store 'to' and 'from' so the worker knows who to notify if it times out
		String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);
		Map<String, String> data = new HashMap<>();
		data.put(CallSessionMetadata.TO.getKey(), to);
		data.put(CallSessionMetadata.FROM.getKey(), from);
		data.put(CallSessionMetadata.CALL_TYPE.getKey(), callType);
		data.put(CallSessionMetadata.TENANT_ID.getKey(), tenantId.toString()); 

		redisTemplate.opsForHash().putAll(metaKey, data);

		redisTemplate.expire(metaKey, callSessionMetadataTtlMinutes, TimeUnit.MINUTES); // Safety expiry

		// 2. Schedule the timeout task in the ZSET
		// The payload is just the sid; the worker will fetch details from the Hash
		redisTemplate.opsForZSet().add(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid, executeAt);
	}

	private String getCallType(String sid) {
		String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);
		// Retrieve call type
		return (String) redisTemplate.opsForHash().get(metaKey, CallSessionMetadata.TENANT_ID.getKey());
	}

	private void handleAccept(String sid) {
		// If the call is accepted, remove the SID from the delayed queue, we MUST kill the 30s timer immediately
		// This prevents the "Missed Call" logic from ever firing
		handleResolution(sid);
	}

	private void handleTerminate(ChannelHandlerContext ctx, String to, String from, String xml, String sid) {
		// Determine call type
		String callType = getCallType(sid);

		// If they rejected it, we MUST kill the 30s timer immediately
		handleResolution(sid);

		// Check if the reason is 'decline' (The "Red Button" action)
		if (xml.contains("<decline/>")) {
			sendCallLog(ctx, from, to, sid, "rejected", "Call Declined", callType);
		} 
		// If the caller hung up before answer (<cancel/>)
		else if (xml.contains("<cancel/>")) {
			sendCallLog(ctx, from, to, sid, "canceled", "Missed Call", callType);
		}
	}

	private void handleResolution(String sid) {
		redisTemplate.opsForZSet().remove(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid);
		redisTemplate.delete(CallSessionRedisKey.CALL_PENDING_PREFIX.getVal() + sid);
	}

	private String extractSid(String xml) {
		Matcher matcher = SID_PATTERN.matcher(xml);
		return matcher.find() ? matcher.group(1) : null;
	}

	private void sendCallLog(ChannelHandlerContext ctx, String from, String to, 
			String sid, String status, String bodyText, String callType ) {
		// 1. Generate a unique ID for this message stanza
		String messageId = java.util.UUID.randomUUID().toString();
		String timestamp = java.time.Instant.now().toString();

		// 2. Construct the XML Stanza
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

		offlineMessageService.save(messageId, toUserKey, fromUserKey, XmppMessageType.HEADLINE.getXmlValue(), xml.toString())
		.doOnError(e -> {
			log.error("Storage failure for message {}: {}", messageId, xml.toString(), e);
		})
		.subscribe();

		// Publish to cluster the message
		clusterMessagePublisher.convertAndSendToUser(messageId, toUserKey, fromUserKey, ChatType.CHAT, xml.toString());

		log.debug("Publishing Call Log: " + xml.toString());
	}
}
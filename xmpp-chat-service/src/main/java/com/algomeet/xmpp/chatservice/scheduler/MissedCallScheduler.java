package com.algomeet.xmpp.chatservice.scheduler;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.CallSessionMetadata;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p><strong>Missed Call Background Worker</strong></p>
 * * <p>The {@code MissedCallScheduler} is a periodic background worker responsible for 
 * detecting timed-out Jingle sessions. It identifies calls that were initiated but 
 * never accepted or terminated within the configured ringing window.</p>
 * * <p><b>Core Workflow:</b></p>
 * <ul>
 * <li>Scans Redis Sorted Set (ZSET) for sessions where the 'score' (epoch time) has passed.</li>
 * <li>Uses atomic Redis operations to ensure only one cluster node processes a specific timeout.</li>
 * <li>Generates XMPP {@code <message type='headline'/>} stanzas for call history.</li>
 * <li>Persists stanzas to MongoDB for offline users and broadcasts them across the cluster.</li>
 * </ul>
 */
@Slf4j
@Component
@AllArgsConstructor
public class MissedCallScheduler {

    private final StringRedisTemplate redisTemplate;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;

    /**
     * Polls Redis every second for tasks that have "matured" 
     * (score <= current timestamp). 
     * * <p>Note: Redis operations here are O(log(N) + M), which is highly efficient even 
     * with thousands of concurrent pending calls.</p>
     */
    @Scheduled(fixedDelay = 1000)
    public void processExpiredCalls() {
        long now = System.currentTimeMillis();

        // 1. Fetch all Session IDs (SIDs) where the timeout threshold has been reached.
        Set<String> expiredSids = redisTemplate.opsForZSet().rangeByScore(CallSessionRedisKey.DELAYED_QUEUE.getVal(), 0, now);

        if (expiredSids == null || expiredSids.isEmpty()) {
            return;
        }

        for (String sid : expiredSids) {
            // 2. ATOMIC LOCK: Attempt to remove the SID from the ZSET.
            // Redis is single-threaded; only the first node to execute this 'remove' will 
            // receive a return value > 0. This prevents duplicate 'Missed Call' logs.
            Long removed = redisTemplate.opsForZSet().remove(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid);
            
            if (removed != null && removed > 0) {
                processMissedCall(sid);
            }
        }
    }

    /**
     * Retrieves session details from Redis and initiates the missed call notification flow.
     * * @param sid The unique Jingle Session ID to process.
     */
    private void processMissedCall(String sid) {
        // Construct the key for the metadata Hash stored during handleInitiate()
        String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);
        
        // 3. Retrieve metadata from Redis Hash
        Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);
        
        if (metadata.isEmpty()) {
            // This happens if the metadata TTL expired or if the call was resolved 
            // but the ZSET entry wasn't cleared correctly.
            log.warn("Found expired SID {} in ZSET, but metadata Hash was already empty.", sid);
            return;
        }

        String to = (String) metadata.get(CallSessionMetadata.TO.getKey());
        String from = (String) metadata.get(CallSessionMetadata.FROM.getKey());
        String type = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());

        log.info("Call session {} timed out. Sending Missed Call log to recipient: {}", sid, to);

        // 4. Build and dispatch the XMPP Headline Stanza
        sendMissedCallStanza(from, to, sid, type);

        // 5. Cleanup: Manually remove the metadata hash to free Redis memory immediately
        redisTemplate.delete(metaKey);
    }

    /**
     * Constructs the XEP-compliant XML stanza and routes it through persistence and cluster pub/sub.
     */
    private void sendMissedCallStanza(String from, String to, String sid, String type) {
        String id = java.util.UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        // XML structure for AlgoMeet call logging (urn:xmpp:algomeet:calls)
        String xml = String.format(
            "<message from='%s' to='%s' type='headline' id='%s'>" +
            "<subject>Missed %s Call</subject>" +
            "<body>Missed %s call</body>" +
            "<call-log xmlns='urn:xmpp:algomeet:calls' type='%s' status='missed' timestamp='%s' sid='%s'/>" +
            "</message>",
            from, to, id, type, type, type, timestamp, sid
        );
        
        String toUserKey = XmppUtil.getUserKey(to);
		String fromUserKey = XmppUtil.getUserKey(from);

		// A. Persist to MongoDB for Offline users. 
        // This ensures the user sees the 'Missed Call' even if they log in much later.
		offlineMessageService.save(id, toUserKey, fromUserKey, XmppMessageType.HEADLINE.getXmlValue(), xml)
		.doOnError(e -> {
			log.error("Failed to persist missed call log for SID {}: {}", sid, e.getMessage());
		})
		.subscribe();

		// B. Publish to Cluster Redis Pub/Sub.
        // If the recipient is currently online on another node, this delivers the log in real-time.
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, xml);
        
		log.debug("Successfully published Missed Call stanza for SID: {}", sid);
    }
}
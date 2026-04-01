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

@Slf4j
@Component
@AllArgsConstructor
public class MissedCallScheduler {

    private final StringRedisTemplate redisTemplate;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;

    /**
     * Polls Redis every second for tasks that have "matured" 
     * (score <= current timestamp)
     */
    @Scheduled(fixedDelay = 1000)
    public void processExpiredCalls() {
        long now = System.currentTimeMillis();

        // 1. Get all SIDs where the timeout has passed
        Set<String> expiredSids = redisTemplate.opsForZSet().rangeByScore(CallSessionRedisKey.DELAYED_QUEUE.getVal(), 0, now);

        if (expiredSids == null || expiredSids.isEmpty()) {
            return;
        }

        for (String sid : expiredSids) {
            // 2. Atomic Remove: Attempt to remove from ZSET to ensure only 1 instance processes this
            Long removed = redisTemplate.opsForZSet().remove(CallSessionRedisKey.DELAYED_QUEUE.getVal(), sid);
            
            if (removed != null && removed > 0) {
                processMissedCall(sid);
            }
        }
    }

    private void processMissedCall(String sid) {
        String metaKey = CallSessionRedisKey.CALL_PENDING_PREFIX.format(sid);
        
        // 3. Retrieve metadata from the Hash
        Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);
        
        if (metadata.isEmpty()) {
            log.warn("Found expired SID {} but no metadata exists in Redis.", sid);
            return;
        }

        String to = (String) metadata.get(CallSessionMetadata.TO.getKey());
        String from = (String) metadata.get(CallSessionMetadata.FROM.getKey());
        String type = (String) metadata.get(CallSessionMetadata.CALL_TYPE.getKey());

        log.info("Call {} timed out. Sending Missed Call notification to {}", sid, to);

        // 4. Trigger the XML Stanza
        sendMissedCallStanza(from, to, sid, type);

        // 5. Cleanup the Hash
        redisTemplate.delete(metaKey);
    }

    private void sendMissedCallStanza(String from, String to, String sid, String type) {
        String id = java.util.UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        // Build the XML you defined earlier
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

		offlineMessageService.save(id, toUserKey, fromUserKey, XmppMessageType.HEADLINE.getXmlValue(), xml.toString())
		.doOnError(e -> {
			log.error("Storage failure for message {}: {}", id, xml.toString(), e);
		})
		.subscribe();

		// Publish to cluster the message
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, xml.toString());
		log.debug("Publishing Call Log: " + xml.toString());
    }
}
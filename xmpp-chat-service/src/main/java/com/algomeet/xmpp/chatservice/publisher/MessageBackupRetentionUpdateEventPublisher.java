package com.algomeet.xmpp.chatservice.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.constant.MessageBackupRetentionFields;
import com.algomeet.common.properties.CommonRedisStreamProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MessageBackupRetentionUpdateEventPublisher {
	@Autowired
	@Qualifier("streamStringRedisTemplate")
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private CommonRedisStreamProperties redisStreamProperties;

	public RecordId publish(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
		// 1. Prepare the payload
		// Note: Redis Streams usually require String values. 
		// If 'message' is a List, it must be serialized (e.g., to JSON).
		Map<String, String> body = new HashMap<>();
		body.put(MessageBackupRetentionFields.USER_KEY, userKey.toString());
		body.put(MessageBackupRetentionFields.PEER_KEY, peerKey.toString());
		body.put(MessageBackupRetentionFields.MESSAGE_RETENTION_DAYS, messageRetentionDays.toString());
		body.put(MessageBackupRetentionFields.TIMESTAMP, String.valueOf(System.currentTimeMillis()));

		// 2. Publish to Redis Stream using standard blocking operations
		try {
			MapRecord<String, String, String> record = MapRecord.create(
					redisStreamProperties.getMessageBackupRetentionUpdateEvents(), 
					body
					);

			RecordId recordId = redisTemplate.opsForStream().add(record);

			log.info("Produced message update message backup retention ID: {}", recordId);
			return recordId;

		} catch (Exception e) {
			log.error("Failed to add to Redis Stream", e);
			throw e; // Rethrowing ensures calling methods know the operation failed
		}
	}
}
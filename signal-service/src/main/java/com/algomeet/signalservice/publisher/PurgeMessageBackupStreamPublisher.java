package com.algomeet.signalservice.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.signalservice.constant.PurgeBackupMessageStream;
import com.algomeet.signalservice.properties.RedisStreamProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PurgeMessageBackupStreamPublisher {
	@Autowired
	@Qualifier("purgeMessageBackupStringRedisTemplate")
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private RedisStreamProperties redisStreamProperties;

	public RecordId publish(UUID userKey) {
		// 1. Prepare the payload
		// Note: Redis Streams usually require String values. 
		// If 'message' is a List, it must be serialized (e.g., to JSON).
		Map<String, String> body = new HashMap<>();
		body.put(PurgeBackupMessageStream.MESSAGE_KEY_USER_KEY, userKey.toString());
		body.put(PurgeBackupMessageStream.MESSAGE_KEY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

		// 2. Publish to Redis Stream using standard blocking operations
		try {
			MapRecord<String, String, String> record = MapRecord.create(
					redisStreamProperties.getPurgeMessageBackup(), 
					body
					);

			RecordId recordId = redisTemplate.opsForStream().add(record);

			log.info("Produced message delete media events ID: {}", recordId);
			return recordId;

		} catch (Exception e) {
			log.error("Failed to add to Redis Stream", e);
			throw e; // Rethrowing ensures calling methods know the operation failed
		}
	}
}
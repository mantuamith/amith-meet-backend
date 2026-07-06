package com.algomeet.xmpp.chatservice.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.constant.MessageBackupRetentionFields;
import com.algomeet.common.properties.CommonRedisStreamProperties;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ReactiveMessageBackupRetentionUpdateEventPublisher {
	private ReactiveRedisTemplate<String, String> redisTemplate;
	private CommonRedisStreamProperties redisStreamProperties;

	public ReactiveMessageBackupRetentionUpdateEventPublisher(
			@Qualifier("reactiveStringRedisTemplate")
			ReactiveRedisTemplate<String, String> redisTemplate,
			CommonRedisStreamProperties redisStreamProperties) {
		this.redisTemplate = redisTemplate;
		this.redisStreamProperties = redisStreamProperties;
	}

	public Mono<RecordId> publish(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
		// 1. Prepare the payload
		Map<String, String> body = new HashMap<>();
		body.put(MessageBackupRetentionFields.USER_KEY, userKey.toString());
		body.put(MessageBackupRetentionFields.PEER_KEY, peerKey.toString());
		body.put(MessageBackupRetentionFields.MESSAGE_RETENTION_DAYS, messageRetentionDays.toString());
		body.put(MessageBackupRetentionFields.TIMESTAMP, String.valueOf(System.currentTimeMillis()));

		// 2. Build the record
		MapRecord<String, String, String> record = MapRecord.create(
				redisStreamProperties.getMessageBackupRetentionUpdateEvents(), 
				body
				);

		// 3. Publish non-blocking stream event
		return redisTemplate.opsForStream()
				.add(record)
				.doOnNext(recordId -> log.info("Produced message update message backup retention ID: {}", recordId))
				.doOnError(e -> log.error("Failed to add to Redis Stream", e));
	}
}
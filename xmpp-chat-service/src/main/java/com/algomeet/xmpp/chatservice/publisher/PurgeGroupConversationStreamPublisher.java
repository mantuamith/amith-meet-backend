package com.algomeet.xmpp.chatservice.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.constant.PurgeGroupConversationFields;
import com.algomeet.xmpp.chatservice.properties.RedisStreamProperties;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class PurgeGroupConversationStreamPublisher {
	@Autowired
	private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

	@Autowired
	private RedisStreamProperties redisStreamProperties;

	public Mono<RecordId> publish(UUID groupId) {
		// 1. Prepare the payload
		// Note: Redis Streams usually require String values. 
		// If 'message' is a List, it must be serialized (e.g., to JSON).
		Map<String, String> body = new HashMap<>();
		body.put(PurgeGroupConversationFields.MESSAGE_KEY_GROUP_ID, groupId.toString());
		body.put(PurgeGroupConversationFields.MESSAGE_KEY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

		// Ensure you are using the Reactive template
		return reactiveRedisTemplate.opsForStream()
				.add(MapRecord.create(redisStreamProperties.getPurgeGroupConversation(), body))
				.doOnNext(recordId ->  log.info("Produced purge group conversation message ID: {}", recordId))
				.doOnError(e -> log.error("Failed to add to Redis Stream", e));
	}
}
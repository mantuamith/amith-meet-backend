package com.algomeet.xmpp.chatservice.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.properties.CommonRedisStreamProperties;
import com.algomeet.xmpp.chatservice.constant.MessageMediaDeleteStream;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class MessageMediaDeleteEventPublisher {
	@Autowired
	private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

	@Autowired
	private CommonRedisStreamProperties redisStreamProperties;

	public Mono<RecordId> publish(String userKey, Set<String> mediaIds, Set<String> deleteWithUserKeys,
            String groupId, String messageId) {
		// 1. Prepare the payload
		// Note: Redis Streams usually require String values. 
		// If 'message' is a List, it must be serialized (e.g., to JSON).
		Map<String, String> body = new HashMap<>();
		body.put(MessageMediaDeleteStream.MESSAGE_KEY_USER_KEY, userKey); 
		body.put(MessageMediaDeleteStream.MESSAGE_KEY_MEDIA_IDS, String.join(",", mediaIds)); 
		body.put(MessageMediaDeleteStream.MESSAGE_KEY_DELETE_WITH_USER_KEYS, String.join(",", deleteWithUserKeys)); 
		body.put(MessageMediaDeleteStream.MESSAGE_KEY_GROUP_ID, groupId);
		body.put(MessageMediaDeleteStream.MESSAGE_KEY_MESSAGE_ID, messageId);
		body.put(MessageMediaDeleteStream.MESSAGE_KEY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

		// Ensure you are using the Reactive template
		return reactiveRedisTemplate.opsForStream()
				.add(MapRecord.create(redisStreamProperties.getMessageMediaDeleteEvents(), body))
				.doOnNext(recordId ->  log.info("Produced message delete media events ID: {}", recordId))
				.doOnError(e -> log.error("Failed to add to Redis Stream", e));
	}
}
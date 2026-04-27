package com.algomeet.xmpp.chatservice.publisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.constant.MissedCallStream;
import com.algomeet.xmpp.chatservice.properties.RedisStreamProperties;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class MissedCallStreamPublisher {
	@Autowired
	private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

	@Autowired
	private RedisStreamProperties redisStreamProperties;

	public Mono<RecordId> publish(List<String> message, String chatType) {
		// 1. Prepare the payload
		// Note: Redis Streams usually require String values. 
		// If 'message' is a List, it must be serialized (e.g., to JSON).
		Map<String, String> body = new HashMap<>();
		body.put(MissedCallStream.MESSAGE_KEY_MESSAGE, String.join(",", message)); // Or use Jackson for JSON
		body.put(MissedCallStream.MESSAGE_KEY_CHAT_TYPE, chatType);
		body.put(MissedCallStream.MESSAGE_KEY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

		// Ensure you are using the Reactive template
		return reactiveRedisTemplate.opsForStream()
				.add(MapRecord.create(redisStreamProperties.getMissedCall(), body))
				.doOnNext(recordId ->  log.info("Produced missed call message ID: {}", recordId))
				.doOnError(e -> log.error("Failed to add to Redis Stream", e));
	}
}
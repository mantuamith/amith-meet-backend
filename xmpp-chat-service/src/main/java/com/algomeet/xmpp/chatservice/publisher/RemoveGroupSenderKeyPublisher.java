package com.algomeet.xmpp.chatservice.publisher;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.constant.RemoveGroupSenderKeyFields;
import com.algomeet.common.properties.CommonRedisStreamProperties;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class RemoveGroupSenderKeyPublisher {
	@Autowired
	private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

	@Autowired
	private CommonRedisStreamProperties commonRedisStreamProperties;

	public Mono<RecordId> publish(String groupId, String userKey) {	    
	    Map<String, String> body = new HashMap<>();
	    
	    // Always present fields	    
	    body.put(RemoveGroupSenderKeyFields.TIMESTAMP, String.valueOf(System.currentTimeMillis()));

	    // Conditionally add fields only if they are not null/empty
	    if (groupId != null) {
	        body.put(RemoveGroupSenderKeyFields.GROUP_ID, groupId);
	    }
	    
	    if (userKey != null) {
	    	body.put(RemoveGroupSenderKeyFields.USER_KEY, userKey); 
	    }
	  
	    return reactiveRedisTemplate.opsForStream()
	            .add(MapRecord.create(commonRedisStreamProperties.getRemoveGroupSenderKeyEvents(), body))
	            .doOnNext(recordId -> log.info("Produced remove group sender key message ID: {}", recordId))
	            .doOnError(e -> log.error("Failed to add to Redis Stream", e));
	}
}
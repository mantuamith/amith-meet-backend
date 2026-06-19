package com.algomeet.signalservice.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.constant.MessageMediaDeleteStream;
import com.algomeet.common.properties.CommonRedisStreamProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MessageMediaDeleteEventPublisher {

	@Autowired
	@Qualifier("deleteMediaStringRedisTemplate")
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private CommonRedisStreamProperties redisStreamProperties;

	public RecordId publish(String userKey, Set<String> mediaIds, Set<String> deleteWithUserKeys,
            String groupId, String messageId) {
		
		// 1. Prepare the payload
		Map<String, String> body = new HashMap<>();
		// Always present fields	    
	    body.put(MessageMediaDeleteStream.MESSAGE_KEY_MESSAGE_ID, messageId);
	    body.put(MessageMediaDeleteStream.MESSAGE_KEY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

	    // Conditionally add fields only if they are not null/empty
	    if (groupId != null) {
	        body.put(MessageMediaDeleteStream.MESSAGE_KEY_GROUP_ID, groupId);
	    }
	    
	    if (userKey != null) {
	    	body.put(MessageMediaDeleteStream.MESSAGE_KEY_USER_KEY, userKey); 
	    }
	    
	    if (mediaIds != null && !mediaIds.isEmpty()) {
	        body.put(MessageMediaDeleteStream.MESSAGE_KEY_MEDIA_IDS, String.join(",", mediaIds));
	    }
	    
	    if (deleteWithUserKeys != null && !deleteWithUserKeys.isEmpty()) {
	        body.put(MessageMediaDeleteStream.MESSAGE_KEY_DELETE_WITH_USER_KEYS, String.join(",", deleteWithUserKeys));
	    }

		// 2. Publish to Redis Stream using standard blocking operations
		try {
			MapRecord<String, String, String> record = MapRecord.create(
					redisStreamProperties.getMessageMediaDeleteEvents(), 
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
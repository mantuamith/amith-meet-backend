package com.algomeet.notificationservice.publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.properties.RedisStreamConfigProperties;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class NotificationStreamPublisher {
	@Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
	private RedisStreamConfigProperties redisStreamConfigProperties;

    public void publish(String message) {
        Map<String, String> body = new HashMap<>();
        body.put(Constants.REDIS_STREAM_MESSAGE_KEY_MESSAGE, message);
        body.put(Constants.REDIS_STREAM_MESSAGE_KEY_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        RecordId recordId = redisTemplate.opsForStream().add(MapRecord.create(redisStreamConfigProperties.getNotificationStreamKey(), body));
        log.debug("Produced message ID: {} ", recordId);
    }
}
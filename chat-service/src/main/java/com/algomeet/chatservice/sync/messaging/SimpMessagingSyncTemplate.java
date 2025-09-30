package com.algomeet.chatservice.sync.messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import com.algomeet.chatservice.exception.MessagingSyncException;
import com.algomeet.chatservice.sync.dto.ChatMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SimpMessagingSyncTemplate {
	@Autowired
	private RedisTemplate<String, ChatMessage> redisTemplate;
	
	@Autowired
	private ChannelTopic topic;
	
	public void convertAndSendToUser(String to, String destination, Object payload) {
		convertAndSendToUser(to, destination, payload, null);
	}
	
	public void convertAndSendToUser(String to, String destination, Object payload, String from) {
		try {						
			ChatMessage message = ChatMessage.builder()
					.to(to)
					.destination(destination)
					.payload(payload)
					.from(from)
					.build();
			log.info("Publish: {}", message);

			redisTemplate.convertAndSend(topic.getTopic(), message);
		} catch(Exception ex) {
			log.error("Error publishing message to redis", ex);
			throw new MessagingSyncException("Error publishing to redis topic", ex);
		}
	}
}

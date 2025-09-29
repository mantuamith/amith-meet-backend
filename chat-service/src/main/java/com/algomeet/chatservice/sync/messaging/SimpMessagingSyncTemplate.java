package com.algomeet.chatservice.sync.messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

import com.algomeet.chatservice.sync.dto.ChatMessage;

@Component
public class SimpMessagingSyncTemplate {
	@Autowired
	private RedisTemplate<String, String> redisTemplate;
	
	@Autowired
	private ChannelTopic topic;
	 
	public void convertAndSendToUser(String user, String destination, Object payload) throws MessagingException {		
		ChatMessage message = ChatMessage.builder()
		.user(user)
		.destination(destination)
		.payload(payload)
		.build();
		
		redisTemplate.convertAndSend(topic.getTopic(), message);
	}
}

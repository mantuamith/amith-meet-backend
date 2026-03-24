package com.algomeet.xmpp.chatservice.cluster.publisher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.dto.ClusterSyncMessage;
import com.algomeet.xmpp.chatservice.exceptions.ClusterMessageException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ClusterMessagePublisher {
	@Autowired
	private RedisTemplate<String, ClusterSyncMessage> redisTemplate;
	
	@Autowired
	private ChannelTopic topic;
	
	public void convertAndSendToUser(String id, String to, String from, String payload) {
		try {						
			ClusterSyncMessage message = ClusterSyncMessage.builder()
					.id(id)
					.to(to)					
					.from(from)
					.payload(payload)
					.build();
			log.info("Publish: {}", message);

			redisTemplate.convertAndSend(topic.getTopic(), message);
		} catch(Exception ex) {
			log.error("Error publishing message to redis", ex);
			throw new ClusterMessageException("Error publishing to redis topic", ex);
		}
	}
}

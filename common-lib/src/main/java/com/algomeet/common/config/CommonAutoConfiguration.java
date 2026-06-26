package com.algomeet.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.algomeet.common.properties.CommonRedisStreamProperties;
import com.algomeet.common.properties.CommonRedisTopicProperties;
import com.algomeet.common.redis.lock.ChatMessageRetentionLockManager;
import com.algomeet.common.redis.lock.MucMessageRetentionLockManager;

@Configuration
@Import({CommonRedisStreamProperties.class, CommonRedisTopicProperties.class, RedisConfig.class})
public class CommonAutoConfiguration {	
	
	@Bean
	@ConditionalOnMissingBean
	public ChatMessageRetentionLockManager getChatMessageRetentionLockManager() {
		return new ChatMessageRetentionLockManager();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public MucMessageRetentionLockManager getMucMessageRetentionLockManager() {
		return new MucMessageRetentionLockManager();
	}
}

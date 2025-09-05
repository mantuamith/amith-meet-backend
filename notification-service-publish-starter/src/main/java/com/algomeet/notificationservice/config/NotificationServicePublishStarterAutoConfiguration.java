package com.algomeet.notificationservice.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.event.listener.NotificationEventListener;
import com.algomeet.notificationservice.properties.RedisStreamConfigProperties;
import com.algomeet.notificationservice.publisher.NotificationStreamPublisher;
import com.algomeet.notificationservice.service.NotificationService;


@EnableAsync
@Configuration
@Import({JacksonConfig.class})
public class NotificationServicePublishStarterAutoConfiguration implements InitializingBean{
	@Value("${spring.redis.host:#{null}}")
	private String redisHost;
	
	public void afterPropertiesSet() {
	    if (!StringUtils.hasText(redisHost)) {
            throw new IllegalStateException(
                "Missing required configuration property: spring.redis.host"
            );
        }
	 }
		
	@Bean
	@ConditionalOnMissingBean
	public NotificationService getNotificationService() {
		return new NotificationService();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public NotificationEventListener getNotificationEventListener() {
		return new NotificationEventListener();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public NotificationStreamPublisher getNotificationPublisher() {
		return new NotificationStreamPublisher();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public RedisStreamConfigProperties getRedisStreamConfigProperties() {
		return new RedisStreamConfigProperties();
	}
}

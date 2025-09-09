package com.algomeet.notificationservice.properties;


import org.springframework.beans.factory.annotation.Value;

import lombok.Data;

@Data
public class RedisStreamConfigProperties {
	@Value("${redis.notification.stream.key:notification-stream}")
	private String notificationStreamKey;
}

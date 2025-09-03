package com.algomeet.notificationservice.properties;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class RedisStreamConfigProperties {
	@Value("${redis.notification.stream.key:notification-stream}")
	private String notificationStreamKey;
}

package com.algomeet.signalservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "redis.topic")
public class RedisTopicProperties {
    private String e2eeEvents = "e2ee-event-topic";
}


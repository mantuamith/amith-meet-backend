package com.algomeet.xmpp.chatservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "redis.stream")
public class RedisStreamProperties {

    /**
     * Redis stream key for storing missed call events.
     */
    private String missedCall = "missed-call-stream";
}
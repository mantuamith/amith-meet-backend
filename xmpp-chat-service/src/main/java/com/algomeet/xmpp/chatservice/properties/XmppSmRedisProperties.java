package com.algomeet.xmpp.chatservice.properties;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "xmpp.sm.redis")
public class XmppSmRedisProperties {

    /**
     * TTL for SM session data in Redis (used for resume window).
     */
    private Duration ttl = Duration.ofHours(24); // default fallback
}
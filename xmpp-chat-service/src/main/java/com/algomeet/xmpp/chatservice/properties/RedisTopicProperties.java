package com.algomeet.xmpp.chatservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "redis.topic")
public class RedisTopicProperties {
    /** The name of the Redis channel used for broadcasting synchronization events across nodes. */
    private String clusterSync = "cluster-sync-topic";
    
    private String e2eeEvents = "e2ee-event-topic";
}

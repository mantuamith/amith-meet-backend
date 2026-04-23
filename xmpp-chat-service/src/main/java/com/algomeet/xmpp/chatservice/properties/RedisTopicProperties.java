package com.algomeet.xmpp.chatservice.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class RedisTopicProperties {
    /** The name of the Redis channel used for broadcasting synchronization events across nodes. */
    @Value("${cluster.sync.topic:cluster-sync-topic}")
    private String clusterSyncTopic;
    
    @Value("${event.e2ee.topic:e2ee-event-topic}")
    private String e2eeEventTopic;
}

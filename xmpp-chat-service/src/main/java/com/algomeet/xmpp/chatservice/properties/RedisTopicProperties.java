package com.algomeet.xmpp.chatservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for Redis Pub/Sub topics.
 *
 * <p>
 * These topics are used for inter-node communication in a clustered environment,
 * enabling real-time synchronization and event propagation across multiple instances.
 *
 * Example configuration:
 *
 * <pre>
 * redis:
 *   topic:
 *     cluster-sync: cluster-sync-topic
 *     e2ee-events: e2ee-event-topic
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "redis.topic")
public class RedisTopicProperties {

    /**
     * Redis topic used for broadcasting cluster synchronization events.
     *
     * These events are used to keep multiple application nodes in sync,
     * such as cache updates, session changes, or distributed state changes.
     *
     * Default: cluster-sync-topic
     */
    private String clusterSync = "cluster-sync-topic";

    /**
     * Redis topic used for End-to-End Encryption (E2EE) related events.
     *
     * This includes events such as key updates, device synchronization,
     * and secure messaging state propagation.
     *
     * Default: e2ee-event-topic
     */
    private String e2eeEvents = "e2ee-event-topic";
}
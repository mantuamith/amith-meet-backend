package com.algomeet.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
 *     e2ee-events: e2ee-event-topic
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "common.redis.topic")
public class CommonRedisTopicProperties {

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
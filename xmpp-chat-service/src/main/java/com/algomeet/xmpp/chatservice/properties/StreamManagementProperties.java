package com.algomeet.xmpp.chatservice.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for XMPP Stream Management (SM).
 *
 * <p>
 * These settings control temporary session state stored in Redis,
 * stanza buffering limits, and cleanup scheduling.
 *
 * Example:
 *
 * <pre>
 * sm:
 *   session:
 *     redis-ttl: 60s
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "xmpp.sm")
public class StreamManagementProperties {

    /**
     * Stream Management session configuration.
     */
    private Session session = new Session();

    @Data
    public static class Session {

        /**
         * Time-to-live for Stream Management session data stored in Redis.
         *
         * Expired sessions may no longer be resumable.
         *
         * Default: 60 seconds
         */
        private Duration resumeTtl = Duration.ofSeconds(60);
    }
}
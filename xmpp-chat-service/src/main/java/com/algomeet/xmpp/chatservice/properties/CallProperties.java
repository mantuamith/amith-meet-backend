package com.algomeet.xmpp.chatservice.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for call-related behaviors.
 *
 * <p>
 * Values are loaded from application properties or YAML files
 * using the prefix {@code call}.
 *
 * Example:
 *
 * <pre>
 * call.ringing-timeout=30s
 * call.session-metadata-ttl=5m
 * </pre>
 *
 * These settings are used to control call lifecycle timing,
 * expiration, and temporary call session metadata retention.
 */
@Data
@Component
@ConfigurationProperties(prefix = "call")
public class CallProperties {

    /**
     * Maximum duration a call is allowed to stay in ringing state
     * before it is automatically marked as missed, cancelled,
     * or timed out by the server.
     *
     * Default: 30 seconds
     */
    private Duration ringingTimeout = Duration.ofSeconds(30);

    /**
     * Time-to-live (TTL) for temporary call session metadata stored
     * in cache or persistence layers.
     *
     * Example metadata:
     * - call session ID
     * - caller / callee info
     * - room call state
     * - bridge assignment
     *
     * After this duration, stale metadata may be automatically removed.
     *
     * Default: 5 minutes
     */
    private Duration sessionMetadataTtl = Duration.ofMinutes(5);

}
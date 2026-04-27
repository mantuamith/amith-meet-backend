package com.algomeet.xmpp.chatservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for XMPP server domain settings.
 *
 * <p>
 * These properties define the base XMPP domain and the domain used for
 * multi-user chat (MUC) / group chat services.
 *
 * Example configuration:
 *
 * <pre>
 * xmpp:
 *   server:
 *     domain: algomeet.app
 *     group-chat-domain: conference.algomeet.app
 * </pre>
 *
 * These values are used when constructing JIDs for users, rooms,
 * and routing XMPP stanzas across services.
 */
@Data
@Component
@ConfigurationProperties(prefix = "xmpp.server")
public class DomainProperties {

    /**
     * Primary XMPP server domain used for user JIDs.
     *
     * Example:
     * user@algomeet.app
     */
    private String domain;

    /**
     * Domain used for group chat / Multi-User Chat (MUC) rooms.
     *
     * Example:
     * room@conference.algomeet.app
     */
    private String groupChatDomain;
}
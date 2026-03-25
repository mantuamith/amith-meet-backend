package com.algomeet.xmpp.chatservice.enums;

import java.util.Arrays;

/**
 * Represents XMPP Message Types as per RFC 6121 and XEP-0160.
 */
public enum XmppMessageType {
    /**
     * Messages with type "normal" or no type. SHOULD be stored offline.
     */
    NORMAL("normal", true),

    /**
     * Standard chat messages. SHOULD be stored offline (excluding chat states).
     */
    CHAT("chat", true),

    /**
     * Multi-user chat messages. SHOULD NOT be stored offline.
     */
    GROUPCHAT("groupchat", false),

    /**
     * Time-sensitive alerts/news. SHOULD NOT be stored offline.
     */
    HEADLINE("headline", false),

    /**
     * Error stanzas. SHOULD NOT be stored offline.
     */
    ERROR("error", false);

    private final String xmlValue;
    private final boolean supportsOfflineStorage;

    XmppMessageType(String xmlValue, boolean supportsOfflineStorage) {
        this.xmlValue = xmlValue;
        this.supportsOfflineStorage = supportsOfflineStorage;
    }

    public String getXmlValue() {
        return xmlValue;
    }

    public boolean supportsOfflineStorage() {
        return supportsOfflineStorage;
    }

    /**
     * Helper to find the enum from an XML attribute string.
     * Per RFC 6121, if 'type' is missing, it defaults to NORMAL.
     */
    public static XmppMessageType fromString(String type) {
        if (type == null || type.isEmpty()) {
            return NORMAL;
        }
        return Arrays.stream(values())
                .filter(t -> t.xmlValue.equalsIgnoreCase(type))
                .findFirst()
                .orElse(NORMAL); // Default to normal for unknown types
    }
}
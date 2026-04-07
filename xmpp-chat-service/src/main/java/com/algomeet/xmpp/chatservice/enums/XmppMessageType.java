package com.algomeet.xmpp.chatservice.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents XMPP Stanza Types as per RFC 6120/6121.
 * Note: 'set' and 'get' are technically IQ types, but included here for routing consistency.
 */
public enum XmppMessageType {
    /**
     * Messages with type "normal" or no type. SHOULD be stored offline.
     */
    NORMAL("normal", true),

    /**
     * Standard chat messages. SHOULD be stored offline.
     */
    CHAT("chat", true),

    /**
     * Multi-user chat messages. (MUC archive handles this instead).
     */
    GROUPCHAT("groupchat", true),

    /**
     * Time-sensitive alerts/news. 
     */
    HEADLINE("headline", false),

    /**
     * Error stanzas. SHOULD NOT be stored offline.
     */
    ERROR("error", false),

    /**
     * IQ 'set' type. Used for requests that change state (like Jingle session-initiate).
     * NEVER stored offline.
     */
    SET("set", false),

    /**
     * IQ 'get' type. Used for information queries.
     * NEVER stored offline.
     */
    GET("get", false),

    /**
     * IQ 'result' type. Used for successful responses.
     */
    RESULT("result", false);

    private final String xmlValue;
    private final boolean supportsOfflineStorage;
    private static final Map<String, XmppMessageType> LOOKUP_MAP = new HashMap<>();

    static {
        for (XmppMessageType type : values()) {
            LOOKUP_MAP.put(type.xmlValue.toLowerCase(), type);
        }
    }
    
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
     * Defaults to NORMAL for unknown message types, but returns null/specific 
     * logic for IQ types if needed.
     */
    public static XmppMessageType fromString(String type) {
        if (type == null || type.isEmpty()) return NORMAL;
        
        // O(1) Hash lookup is significantly faster than O(N) Stream
        XmppMessageType match = LOOKUP_MAP.get(type.toLowerCase());
        return (match != null) ? match : NORMAL;
    }
}
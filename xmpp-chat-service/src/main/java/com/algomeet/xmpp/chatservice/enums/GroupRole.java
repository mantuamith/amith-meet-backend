package com.algomeet.xmpp.chatservice.enums;

import java.util.HashMap;
import java.util.Map;

public enum GroupRole {
    OWNER,
    ADMIN,
    MEMBER,
    VISITOR;
	
	private static final Map<String, GroupRole> LOOKUP_MAP = new HashMap<>();

    static {
        for (GroupRole type : values()) {
            LOOKUP_MAP.put(type.name().toLowerCase(), type);
        }
    }
    
    /**
     * Helper to find the enum from an role string.
     * Defaults to VISITOR for unknown message types, but returns null/specific 
     * logic for IQ types if needed.
     */
    public static GroupRole fromString(String type) {
        if (type == null || type.isEmpty()) return VISITOR;
        
        // O(1) Hash lookup is significantly faster than O(N) Stream
        GroupRole match = LOOKUP_MAP.get(type.toLowerCase());
        return (match != null) ? match : VISITOR;
    }
}
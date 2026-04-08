package com.algomeet.xmpp.chatservice.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the persistent, long-term relationship between a user and a MUC room.
 * Affiliations are stored in the database and survive across multiple sessions.
 * * Hierarchy (High to Low): OWNER > ADMIN > MEMBER > NONE > OUTCAST.
 */
public enum MucAffiliation {

    /**
     * The creator or designated controller of the room.
     * Can destroy the room, change any configuration, and manage other Owners/Admins.
     */
    OWNER("owner"),

    /**
     * A room manager with high-level permissions.
     * Can manage the member list, kick occupants, and change roles.
     */
    ADMIN("admin"),

    /**
     * A whitelisted user. 
     * In "members-only" rooms, only entities with this affiliation (or higher) 
     * are permitted to enter.
     */
    MEMBER("member"),

    /**
     * A user with no specific persistent relationship to the room.
     * This is the default state for transient visitors in a public room.
     */
    NONE("none"),

    /**
     * A banned user. 
     * Entities with this affiliation are strictly forbidden from entering the room.
     */
    OUTCAST("outcast");

    private final String value;
    private static final Map<String, MucAffiliation> LOOKUP_MAP = new HashMap<>();

    static {
        for (MucAffiliation type : values()) {
            LOOKUP_MAP.put(type.value, type);
        }
    }

    MucAffiliation(String value) {
        this.value = value;
    }

    /**
     * Returns the raw string value as used in XMPP stanzas (e.g., 'owner', 'member').
     */
    public String getValue() {
        return value;
    }

    /**
     * Safely finds the enum from a provided affiliation string.
     * * @param type The affiliation attribute from a MUC stanza.
     * @return The corresponding MucAffiliation, or NONE if null/unrecognized.
     */
    public static MucAffiliation fromString(String type) {
        if (type == null || type.isEmpty()) {
            return NONE;
        }
        
        // O(1) Hash lookup for high-concurrency Netty processing
        MucAffiliation match = LOOKUP_MAP.get(type.toLowerCase());
        return (match != null) ? match : NONE;
    }
}
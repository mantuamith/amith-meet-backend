package com.algomeet.xmpp.chatservice.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the temporary, session-based roles of a MUC occupant as defined in XEP-0045.
 * Unlike Affiliations, Roles are transient and exist only for the duration of the 
 * user's presence in the room.
 */
public enum MucRole {

    /**
     * An occupant who has the highest level of temporary authority.
     * Can kick occupants, mute (change role to visitor), and grant voice.
     */
    MODERATOR("moderator"),

    /**
     * A standard occupant who has "voice" in the room.
     * Allowed to send groupchat messages and initiate Jingle sessions.
     */
    PARTICIPANT("participant"),

    /**
     * A "muted" occupant who can receive messages but lacks "voice".
     * Any groupchat messages sent by a visitor must be rejected with a 403 Forbidden error.
     */
    VISITOR("visitor"),

    /**
     * An entity that is not currently an occupant of the room.
     * Used when a user is kicked, leaves, or has not yet joined.
     */
    NONE("none");

    private final String value;
    private static final Map<String, MucRole> LOOKUP = new HashMap<>();

    static {
        for (MucRole role : values()) {
            LOOKUP.put(role.value, role);
        }
    }

    MucRole(String value) {
        this.value = value;
    }

    /**
     * @return The raw string value for use in XMPP stanzas.
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a string role into its corresponding Enum.
     * Defaults to NONE if the input is null or unrecognized to prevent 
     * accidental privilege escalation.
     * * @param value The 'role' attribute from a MUC admin or presence stanza.
     * @return The matching MucRole or MucRole.NONE.
     */
    public static MucRole fromString(String value) {
        if (value == null) return NONE;
        return LOOKUP.getOrDefault(value.toLowerCase(), NONE);
    }
}
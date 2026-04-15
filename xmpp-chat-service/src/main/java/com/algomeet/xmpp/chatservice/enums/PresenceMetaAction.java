package com.algomeet.xmpp.chatservice.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents custom metadata actions carried inside MUC presence extensions
 * (e.g. <x xmlns='http://algomeet.app/protocol/muc#meta'>).
 *
 * <p>
 * These actions are used to signal client-driven state changes that are not
 * part of standard XMPP MUC semantics (e.g. invite acceptance).
 * </p>
 *
 * Example XML:
 * <pre>
 *   <action>invite_accept</action>
 * </pre>
 */
public enum PresenceMetaAction {

    /**
     * Indicates that a user has accepted a MUC invitation.
     * This is a client-originated intent signal used for routing
     * and join-context classification.
     */
    INVITE_ACCEPT("invite_accept");

    private final String value;

    /**
     * Fast lookup cache for string → enum resolution.
     *
     * <p>
     * Uses a HashMap for O(1) average lookup instead of iterating over values(),
     * which would be O(n) per request.
     * </p>
     */
    private static final Map<String, PresenceMetaAction> LOOKUP_MAP = new HashMap<>();

    static {
        // Populate lookup map once at class loading time
        for (PresenceMetaAction type : values()) {
            LOOKUP_MAP.put(type.value.toLowerCase(), type);
        }
    }

    PresenceMetaAction(String value) {
        this.value = value;
    }

    /**
     * Returns the wire-format string representation used in XML payloads.
     *
     * @return raw string value (e.g. "invite_accept")
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolves a string value into its enum representation.
     *
     * <p>
     * Lookup is case-insensitive and uses a precomputed HashMap for performance.
     * Returns null if the input is null, empty, or not recognized.
     * </p>
     *
     * @param type raw string from XML payload
     * @return matching enum constant, or null if not found
     */
    public static PresenceMetaAction fromString(String type) {
        if (type == null || type.isEmpty()) return null;

        // Normalize input for case-insensitive lookup
        PresenceMetaAction match = LOOKUP_MAP.get(type.toLowerCase());

        return (match != null) ? match : null;
    }
}
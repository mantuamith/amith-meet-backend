package com.algomeet.xmpp.chatservice.util;

import java.util.Set;

/**
 * Utility to identify XMPP elements that increment the 'h' (handled) count.
 * Per XEP-0198, only the three core stanzas are trackable.
 */
public class XmppStanzaUtil {

    /**
     * The set of top-level stanzas defined in RFC 6120.
     */
    private static final Set<String> TRACKABLE_STANZAS = Set.of("message", "iq", "presence");

    /**
     * Determines if an XML element requires an acknowledgement.
     * * @param elementName The root name of the XML tag (e.g., "message", "r", "a").
     * @return true if the element is a trackable stanza, false if it is a stream control element.
     */
    public static boolean requiresAck(String elementName) {
        if (elementName == null || elementName.isBlank()) {
            return false;
        }
        // Normalize to lowercase as XMPP tags are case-sensitive (usually lowercase)
        return TRACKABLE_STANZAS.contains(elementName.trim().toLowerCase());
    }
}
package com.algomeet.xmpp.chatservice.util;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Utility to identify XMPP elements that increment the 'h' (handled) count.
 * Per XEP-0198, only the three core stanzas are trackable.
 */
public class XmppStanzaUtil {
    private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();
    
    /**
     * Performs a lightweight parse of the top-level element attributes (to, from, id, type).
     * This avoids expensive full XML unmarshalling for simple routing decisions.
     */
    public static Map<String, String> parseStanzaAttributes(String xml) throws XMLStreamException {
        Map<String, String> attrMap = new HashMap<>();
        try (StringReader sr = new StringReader(xml)) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(sr);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            attrMap.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                        }
                        break;
                    }
                }
            } finally {
                reader.close();
            }
        }
        return attrMap;
    }
    
    /**
     * Determines if the stanza contains actual conversational content 
     * that requires long-term storage or user notification.
     * Filters out transient states (typing), presence updates, and MUC signaling.
     */
    public static boolean isArchiveable(String xml) {
        // 1. Check for Jingle Signaling (VoIP/Video/File Transfer)
        if (xml.contains("urn:xmpp:jingle:1")) { 
            // ROUTE ONLY: This is a call setup, do not touch the DB
            return false;
        }

        // 2. Check for Chat States (Typing, Paused, Gone)
        if (xml.contains("http://jabber.org/protocol/chatstates") && !xml.contains("<body")) {
            // ROUTE ONLY: Transient engagement signal
            return false;
        }

        // 3. Check for MUC Presence (Join/Update/Leave Group)
        if (xml.contains("<presence") && xml.contains("http://jabber.org/protocol/muc")) {
            // ROUTE ONLY: Membership/Occupancy logic, do not save to message history
            return false;
        }

        // 4. Check for Standard Presence (Online, Away, DND)
        if (xml.contains("<presence")) {
            // UPDATE REDIS & ROUTE: Update live state only, no persistent history
            return false;
        }
        
        return true;
    }
    
    /**
     * Determines if the stanza contains actual conversational content 
     * that requires long-term storage or user notification.
     * Filters out non-notifiable states (typing) and presence updates.
     */
    public static boolean isPushEligible(String xml) {

        // 1. Filter out ephemeral Chat States (Typing, Paused, Gone)
        // No need for notifications when a user is simply "composing"
        if (xml.contains("http://jabber.org/protocol/chatstates") && !xml.contains("<body")) {
            return false;
        }

        // 2. Filter out MUC Presence (Join/Update/Leave Group)
        // These are occupancy changes, not messages to the user
        if (xml.contains("<presence") && xml.contains("http://jabber.org/protocol/muc")) {
            return false;
        }

        // 3. Filter out standard Roster Presence updates (Online, Away, DND)
        if (xml.contains("<presence")) {
            return false;
        }

        return true;
    }
}
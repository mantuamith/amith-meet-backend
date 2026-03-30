package com.algomeet.xmpp.chatservice.util;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility to identify XMPP elements that increment the 'h' (handled) count.
 * Per XEP-0198, only the three core stanzas are trackable.
 */
@Slf4j
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
        
        if (xml.contains("urn:xmpp:mam:2")) { 
            // ROUTE ONLY: This is a call setup, do not touch the DB
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
    
    
    /**
     * Extracts the value of a specific field (tag) from the XML.
     * Specifically looks for <value> inside a <field var='varName'>.
     */
    public static String getFieldValue(String xml, String varName) {
        if (!StringUtils.hasText(xml)) return null;
        
        // Regex to find: <field var='varName'><value>DATA</value></field>
        // Supports both ' and " for attributes
        String regex = "<field var=['\"]" + Pattern.quote(varName) + "['\"]>\\s*<value>([^<]+)</value>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(xml);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts the <max> value from the RSM (Result Set Management) block.
     * <set xmlns='http://jabber.org/protocol/rsm'><max>50</max></set>
     */
    public static int getRsmMax(String xml, int defaultValue) {
        if (!xml.contains("<max>")) return defaultValue;

        try {
            String regex = "<max>(\\d+)</max>";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(xml);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("Failed to parse RSM max value, using default: {}", defaultValue);
        }
        return defaultValue;
    }

    /**
     * Extracts an attribute value from the root stanza tag (e.g., 'id', 'to', 'type').
     */
    public static String getAttribute(String xml, String attributeName) {
        if (!StringUtils.hasText(xml)) return null;

        // Regex looks for attributeName='value' or attributeName="value"
        String regex = attributeName + "=['\"]([^'\"]+)['\"]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(xml);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
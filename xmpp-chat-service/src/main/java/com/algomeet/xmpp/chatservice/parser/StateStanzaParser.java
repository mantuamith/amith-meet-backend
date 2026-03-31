package com.algomeet.xmpp.chatservice.parser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.algomeet.xmpp.chatservice.enums.UserState;

import java.io.StringReader;

/**
 * High-performance StAX parser for extracting XMPP User States.
 * This parser distinguishes between Multi-User Chat (MUC) protocols (XEP-0045),
 * Chat States (XEP-0085), and standard Presence (XEP-0186).
 */
public class StateStanzaParser {

    // Reuse factory to improve performance in a high-concurrency Netty environment
    private static final XMLInputFactory FACTORY = XMLInputFactory.newInstance();
    private static final String NS_MUC = "http://jabber.org/protocol/muc";
    private static final String NS_CHATSTATES = "http://jabber.org/protocol/chatstates";

    /**
     * Analyzes an incoming XMPP stanza and maps it to a unified UserState.
     * * @param xml The raw XML stanza (Presence or Chat State).
     * @return The determined UserState, or null if the XML is malformed or irrelevant.
     */
    public static UserState determineState(String xml) {
        try (StringReader stringReader = new StringReader(xml)) {
            XMLStreamReader reader = FACTORY.createXMLStreamReader(stringReader);
            
            boolean isMuc = false;
            String typeAttr = null;
            String showElement = null;

            // Iterate through XML events (Forward-only, memory-efficient)
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    String namespace = reader.getNamespaceURI();

                    // 1. Identify top-level Presence attributes (Available/Unavailable)
                    if ("presence".equals(localName)) {
                        typeAttr = reader.getAttributeValue(null, "type");
                    } 
                    
                    // 2. Detect MUC Extension (The "Payload").
                    // Presence with this extension indicates a Room Join/Config action.
                    if ("x".equals(localName) && NS_MUC.equals(namespace)) {
                        isMuc = true;
                    }

                    // 3. Detect Chat States (XEP-0085).
                    // These are short-lived engagement signals (Typing, Gone, etc.)
                    if (NS_CHATSTATES.equals(namespace)) {
                        if ("active".equals(localName)) return UserState.ACTIVE;
                        if ("inactive".equals(localName)) return UserState.INACTIVE;
                        if ("gone".equals(localName)) return UserState.GONE;
                    }

                    // 4. Extract <show> element value (e.g., dnd, away)
                    if ("show".equals(localName)) {
                        showElement = reader.getElementText();
                    }
                }
            }

            // --- Business Logic Resolution (Priority-based) ---
            
            // PRIORITY 1: Hard termination. If presence is 'unavailable', the user is GONE.
            if ("unavailable".equals(typeAttr)) {
                return UserState.GONE;
            }

            // PRIORITY 2: Explicit 'show' status (UI-driven availability)
            if ("dnd".equals(showElement)) return UserState.DND;
            if ("away".equals(showElement) || "xa".equals(showElement)) return UserState.AWAY;

            // PRIORITY 3: MUC Join Request.
            // If the MUC extension is present, we treat the user as ACTIVE by default.
            if (isMuc) return UserState.ACTIVE;

            // PRIORITY 4: Standard Presence (Roster update)
            if (typeAttr == null || "available".equals(typeAttr)) {
                return UserState.ACTIVE;
            }

        } catch (XMLStreamException e) {
            // Log this as a malformed stanza in your chat-service logs
            // Logic for handling malformed XML goes here
        }
        return null;
    }
}
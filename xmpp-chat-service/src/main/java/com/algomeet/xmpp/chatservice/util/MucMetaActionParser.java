package com.algomeet.xmpp.chatservice.util;

import javax.xml.stream.*;
import java.io.StringReader;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MucMetaActionParser {

    private static final String META_NS = "http://algomeet.app/protocol/muc#meta";
    private static final String ELEMENT_X = "x";
    private static final String ELEMENT_ACTION = "action";

    // Thread-safe factory reuse
    private static final XMLInputFactory FACTORY = buildFactory();

    private MucMetaActionParser() {}

    /**
     * Extracts <action> from custom muc#meta extension.
     *
     * @param xml raw stanza
     * @return Optional action (e.g. invite_accept)
     */
    public static Optional<String> extractAction(String xml) {
        if (xml == null || xml.isEmpty()) {
            return Optional.empty();
        }

        XMLStreamReader reader = null;

        try {
            reader = FACTORY.createXMLStreamReader(new StringReader(xml));

            boolean insideMeta = false;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    String ns = reader.getNamespaceURI();

                    // Enter <x xmlns='muc#meta'>
                    if (ELEMENT_X.equals(local) && META_NS.equals(ns)) {
                        insideMeta = true;
                        continue;
                    }

                    // Extract <action>
                    if (insideMeta && ELEMENT_ACTION.equals(local)) {
                        String value = reader.getElementText();
                        return Optional.ofNullable(value).map(String::trim);
                    }
                }

                if (event == XMLStreamConstants.END_ELEMENT) {
                    // Exit meta block
                    if (insideMeta &&
                        ELEMENT_X.equals(reader.getLocalName()) &&
                        META_NS.equals(reader.getNamespaceURI())) {
                        insideMeta = false;
                    }
                }
            }

        } catch (XMLStreamException ex) {
            log.warn("Failed to parse MUC meta action. payloadSize={}, error={}",
                    xml.length(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during MUC meta parsing", ex);
        } finally {
            closeQuietly(reader);
        }

        return Optional.empty();
    }

    /**
     * Secure XML factory configuration (prevents XXE attacks).
     */
    private static XMLInputFactory buildFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();

        try {
            // SECURITY: Prevent XXE
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

            // PERFORMANCE
            factory.setProperty(XMLInputFactory.IS_COALESCING, true);

        } catch (IllegalArgumentException ignored) {
            // Some implementations may not support all properties
        }

        return factory;
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {}
        }
    }
}
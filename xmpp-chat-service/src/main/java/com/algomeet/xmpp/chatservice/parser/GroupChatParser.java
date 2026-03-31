package com.algomeet.xmpp.chatservice.parser;

import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import lombok.extern.slf4j.Slf4j;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

@Slf4j
public class GroupChatParser {
    private static final XMLInputFactory FACTORY = XMLInputFactory.newInstance();

    static {
        FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    public static StanzaInfo parse(String rawXml) {
        StanzaInfo.StanzaInfoBuilder builder = StanzaInfo.builder().category("groupchat");

        try (StringReader stringReader = new StringReader(rawXml)) {
            XMLStreamReader reader = FACTORY.createXMLStreamReader(stringReader);

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    String ns = reader.getNamespaceURI();

                    if ("message".equals(localName)) {
                        builder.stanzaType("message");
                        builder.stanzaId(reader.getAttributeValue(null, "id"));
                    } 
                    else if ("presence".equals(localName)) {
                        builder.stanzaType("presence");
                        return builder.build(); // Skip further parsing for presence
                    }
                    // Extract Target ID for Reactions (XEP-0444)
                    else if ("reactions".equals(localName) && "urn:xmpp:reactions:0".equals(ns)) {
                        builder.category("reaction");
                        builder.targetId(reader.getAttributeValue(null, "id"));
                    }
                    // Extract Target ID for Fastenings/Retractions (XEP-0422)
                    else if ("apply-to".equals(localName) && "urn:xmpp:fasten:0".equals(ns)) {
                        builder.targetId(reader.getAttributeValue(null, "id"));
                    } 
                    else if ("retract".equals(localName) && "urn:xmpp:retract:0".equals(ns)) {
                        builder.category("retraction");
                    }
                    else if ("encrypted".equals(localName) && ns != null && ns.contains("omemo")) {
                        builder.isE2EE(true);
                    }
                }
            }
        } catch (Exception e) {
            log.error("XML Parse Error: {}", e.getMessage());
        }
        return builder.build();
    }
}
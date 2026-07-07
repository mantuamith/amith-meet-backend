package com.algomeet.xmpp.chatservice.stanza.parser;

import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

@Component
public class PinMessageStaxParser {

    public static class ParsedMessage {
        public String action;
        public String id; // Represents the target messageId being pinned/unpinned

        @Override
        public String toString() {
            return "ParsedMessage{" +
                    "action='" + action + '\'' +
                    ", id='" + id + '\'' +
                    '}';
        }
    }

    /**
     * Parses an incoming XMPP message stanza for custom pin element directives.
     */
    public ParsedMessage parse(String xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        ParsedMessage result = new ParsedMessage();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                // <pin xmlns="urn:xmpp:algomeet:pin:0" ...>
                if ("pin".equals(localName)) {
                    String ns = reader.getNamespaceURI();
                    if ("urn:xmpp:algomeet:pin:0".equals(ns)) {
                        result.action = reader.getAttributeValue(null, "action");
                        result.id = reader.getAttributeValue(null, "id");
                        break; // Found our data, we can stop parsing early
                    }
                }
            }
        }

        reader.close();
        return result;
    }
}
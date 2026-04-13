package com.algomeet.xmpp.chatservice.util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

@Slf4j
@Component
public class XmppReadUtil {

    public static final String NS_DISPLAYS = "urn:xmpp:chat-markers:0";
    private static final String EL_DISPLAYED = "displayed";
    private static final String ATTR_ID = "id";

    private final XMLInputFactory factory;

    public XmppReadUtil() {
        this.factory = XMLInputFactory.newInstance();
        // Disable external entities to prevent XXE attacks
        this.factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        this.factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /**
     * Checks if the stanza is a displayed and returns the original message ID.
     * Returns null if the stanza is not a receipt.
     */
    public String getAckMessageId(String xml) {
        if (xml == null || !xml.contains(NS_DISPLAYS)) {
            return null;
        }

        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(new StringReader(xml));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (EL_DISPLAYED.equals(reader.getLocalName()) && 
                        NS_DISPLAYS.equals(reader.getNamespaceURI())) {
                        return reader.getAttributeValue(null, ATTR_ID);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse XMPP stanza for read detection", e);
        } finally {
            closeReader(reader);
        }
        return null;
    }

    private void closeReader(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
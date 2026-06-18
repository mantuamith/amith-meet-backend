package com.algomeet.xmpp.chatservice.stanza.parser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lombok.extern.slf4j.Slf4j;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class MediaReferenceParser {

    private static final String TARGET_NAMESPACE = "urn:algomeet:file:metadata:0";
    private static final String ELEMENT_MEDIA_REFERENCES = "media-references";
    private static final String ELEMENT_ID = "id";

    /**
     * Parses the XML message and extracts a list of media-reference IDs.
     *
     * @param xmlContent The raw XML string to parse.
     * @return A list of media ID strings found within the target namespace.
     * @throws XMLStreamException If the XML is malformed.
     */
    public static List<UUID> extractMediaIds(String xmlContent) throws XMLStreamException {
        List<UUID> mediaIds = new ArrayList<>();
        
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return mediaIds;
        }

        // 1. Securely configure the XMLInputFactory to prevent XXE attacks
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlContent));
        
        boolean insideTargetMediaReferences = false;

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        String localName = reader.getLocalName();
                        String namespaceURI = reader.getNamespaceURI();

                        // Track if we are inside the correct <media-references> block with the right namespace
                        if (ELEMENT_MEDIA_REFERENCES.equals(localName) && TARGET_NAMESPACE.equals(namespaceURI)) {
                            insideTargetMediaReferences = true;
                        } 
                        // If we are inside the target block and hit an <id> tag, extract its text
                        else if (insideTargetMediaReferences && ELEMENT_ID.equals(localName)) {
                            String id = reader.getElementText().trim();
                            if (!id.isEmpty()) { // Guard against placeholder text                            	
                            	try {
                            	    mediaIds.add(UUID.fromString(id));
                            	} catch (IllegalArgumentException e) {
                            	    // Log a warning and skip the bad ID, or handle it according to company security protocol
                            	     log.warn("Malformed UUID received in stanza: {}", id);
                            	}
                            }
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        // Reset the flag when exiting the target <media-references> block
                        if (ELEMENT_MEDIA_REFERENCES.equals(reader.getLocalName()) && TARGET_NAMESPACE.equals(reader.getNamespaceURI())) {
                            insideTargetMediaReferences = false;
                        }
                        break;
                }
            }
        } finally {
            reader.close();
        }

        return mediaIds;
    }

    // Demo/Verification
    public static void main(String[] args) {
        String xml = "<message to='juliet@shakespeare.lit' from='romeo@montague.lit/resource' id='sharing-a-file'>"
                + "  <file-sharing xmlns='urn:xmpp:sfs:0' disposition='inline'>"
                + "    <file xmlns='urn:xmpp:file:metadata:0'>"
                + "      <media-type>video/mp4</media-type>"
                + "      <name>vacation_video.mp4</name>"
                + "      <size>15728640</size>"
                + "      <desc>Vacation video.</desc>"
                + "    </file>"
                + "    <sources>"
                + "      <url-data xmlns='http://jabber.org/protocol/url-data' target='/media/4f8b9e6c-2d1a-4b7c-8e3f-9a0b1c2d3e4f'/>"
                + "    </sources>"
                + "  </file-sharing>"
                + "  <media-references xmlns='urn:algomeet:file:metadata:0'>"
                + "    <id>4f8b9e6c-2d1a-4b7c-8e3f-9a0b1c2d3e4f</id>"
                + "    <id>7a8b9e6c-2d1a-4b7c-8e3f-9a0b1c2d3e4f</id>" // Testing multiple IDs
                + "  </media-references>"
                + "</message>";

        try {
            List<UUID> ids = extractMediaIds(xml);
            System.out.println("Extracted Media IDs: " + ids);
        } catch (XMLStreamException e) {
            System.err.println("Failed to parse XML: " + e.getMessage());
        }
    }
}
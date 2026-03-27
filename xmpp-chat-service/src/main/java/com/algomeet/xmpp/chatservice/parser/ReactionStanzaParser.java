package com.algomeet.xmpp.chatservice.parser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

@Slf4j
@Component
public class ReactionStanzaParser {

	/**
	 * Parses a raw XMPP message stanza to extract reaction data.
	 * Expected format: XEP-0444
	 */
	public Optional<ReactionData> parse(String rawXml) {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		try {
			XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(rawXml));
			String targetId = null;
			String emoji = null;

			while (reader.hasNext()) {
				int event = reader.next();
				if (event == XMLStreamConstants.START_ELEMENT) {
					String localName = reader.getLocalName();

					if ("reactions".equals(localName)) {
						targetId = reader.getAttributeValue(null, "id");
					} else if ("reaction".equals(localName)) {
						emoji = reader.getElementText();
					}
				}
			}
			if (targetId != null && emoji != null) {
				return Optional.of(new ReactionData(targetId, emoji));
			}
		} catch (Exception e) {
			log.error("StAX Parsing error", e);
		}
		return Optional.empty();
	}

	public record ReactionData(String targetStanzaId, String emoji) {}
}
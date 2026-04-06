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
	 * <p><strong>Optimized Message Archive Filter (XEP-0313 Compliance)</strong></p>
	 * * <p>Determines if a stanza should be persisted to long-term storage (MongoDB).
	 * This method uses a <b>Negative-First, Early-Exit</b> strategy to minimize 
	 * CPU cycles and memory scanning for high-throughput routing.</p>
	 * * @param xml The raw XMPP stanza string.
	 * @return {@code true} if the stanza contains conversational content; 
	 * {@code false} if it is transient signaling.
	 */
	public static boolean isArchiveable(String xmlHeader, String xml) {
		// Defensive check for malformed or empty stream fragments
		if (xml == null) {
			return false;
		}

		// 1. Filter Presence: Standard Roster updates and MUC (XEP-0045) occupancy 
		// are state-based and should never be stored in message history.
		if (XmppStanzaUtil.isPresenceStanza(xmlHeader)) {
			return false; 
		}

		// 2. Conditional Filter for Chat States (XEP-0085):
		// Typing notifications ("is typing...") are transient. We only archive 
		// if the stanza ALSO contains a <body> element (e.g., a message with a state).
		if (xmlHeader.contains("http://jabber.org/protocol/chatstates")) {
			return xml.contains("<body");
		}

		// If it survived the negative filters, it is likely a conversational <message/>
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

	public static boolean isPresenceStanza(String xml) {
		// 1. Find the first actual XML tag
		int firstTag = xml.indexOf('<');

		// 2. Check if that tag starts with "presence" (ignore case, zero allocation)
		if (firstTag != -1 && xml.regionMatches(true, firstTag, "<presence", 0, 9)) {
			return true;
		}
		return false;
	}
}
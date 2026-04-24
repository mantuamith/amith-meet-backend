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

import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.github.f4b6a3.ulid.UlidCreator;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility to identify XMPP elements that increment the 'h' (handled) count.
 * Per XEP-0198, only the three core stanzas are trackable.
 */
@Slf4j
public class XmppStanzaUtil {
	private static final String CHATSTATE = "chatstates";
	private static final String BODY = "<body";
	
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
	public static boolean isArchivable(String xml) {
		// Defensive check for malformed or empty stream fragments
		if (xml == null) {
			return false;
		}

		// 1. Filter Presence: Standard Roster updates and MUC (XEP-0045) occupancy 
		// are state-based and should never be stored in message history.
		if (XmppStanzaUtil.isPresenceStanza(xml)) {
			return false; 
		}

		// 2. Conditional Filter for Chat States (XEP-0085):
		// Typing notifications ("is typing...") are transient. We only archive 
		// if the stanza ALSO contains a <body> element (e.g., a message with a state).
		if (xml.indexOf(CHATSTATE) >= 0) {
			return xml.indexOf(BODY) >= 0;
		}

		// If it survived the negative filters, it is likely a conversational <message/>
		return true;
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
	public static boolean isArchiveableGroupChat(String xml) {
		// Defensive check for malformed or empty stream fragments
		if (xml == null) {
			return false;
		}

		// 1. Filter Presence: Standard Roster updates and MUC (XEP-0045) occupancy 
		// are state-based and should never be stored in message history.
		if (XmppStanzaUtil.isPresenceStanza(xml)) {
			return false; 
		}

		// 2. Conditional Filter for Chat States (XEP-0085):
		// Typing notifications ("is typing...") are transient. We only archive 
		// if the stanza ALSO contains a <body> element (e.g., a message with a state).
		if (xml.indexOf(CHATSTATE) >= 0) {
			return xml.indexOf(BODY) >= 0;
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
	
	public static boolean isMessageStanza(String xml) {
		// 1. Find the first actual XML tag
		int firstTag = xml.indexOf('<');

		// 2. Check if that tag starts with "presence" (ignore case, zero allocation)
		if (firstTag != -1 && xml.regionMatches(true, firstTag, "<message", 0, 8)) {
			return true;
		}
		return false;
	}
	
	public static boolean isJingleStanza(XmppMessageType msgType, String xml) {
		return XmppMessageType.SET == msgType && xml.contains("urn:xmpp:jingle:1");
	}
		
	/**
     * Extracts an attribute value from a specific tag.
     * Example: getAttribute(xml, "item", "nick") returns "pistol"
     */
    public static String getAttribute(String xml, String tagName, String attributeName) {
        if (!StringUtils.hasText(xml)) return null;

        // Regex explanation:
        // <tagName[^>]* -> Find the start of the tag
        // attributeName=['\"] -> Find the attribute key followed by ' or "
        // ([^'\"]+) -> Capture the value until the closing quote
        String regex = "<" + Pattern.quote(tagName) + "[^>]*\\s" + 
                       Pattern.quote(attributeName) + "=['\"]([^'\"]+)['\"]";
        
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(xml);

        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extracts the text content of a direct child tag.
     * Example: getTagContent(xml, "reason") returns "Avaunt, you cullion!"
     */
    public static String getTagContent(String xml, String tagName) {
        if (!StringUtils.hasText(xml)) return null;

        String regex = "<" + Pattern.quote(tagName) + ">([^<]+)</" + Pattern.quote(tagName) + ">";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(xml);

        return matcher.find() ? matcher.group(1) : null;
    }
    
    /**
     * Determines if the incoming XML string is one of the three core XMPP stanzas:
     * <message/>, <presence/>, or <iq/>.
     * * This check is vital for XEP-0198 Stream Management to ensure we only increment
     * the 'h' (handled) counter for top-level stanzas and not for protocol control 
     * elements like <r/>, <a/>, or <sm/>.
     *
     * @param xml The raw XML string from the WebSocket frame.
     * @return true if it is a core stanza, false otherwise.
     */
    public static boolean isCountableStanza(String xml) {
        if (xml == null) return false;

        // 1. Locate the first actual XML tag.
        // XMPP over WebSockets usually doesn't have leading whitespace, 
        // but we look for '<' to be safe.
        int firstTag = xml.indexOf('<');

        // If no '<' is found, it's not valid XML.
        if (firstTag == -1) {
            return false;
        }

        // 2. Perform Case-Insensitive Zero-Allocation Checks
        // We check the first few characters after the '<' to identify the stanza type.
        
        // Check for <message (8 chars)
        if (xml.regionMatches(true, firstTag, "<message", 0, 8)) {
        	return true;
        }

        // Check for <presence (9 chars)
        if (xml.regionMatches(true, firstTag, "<presence", 0, 9)) {
        	return true;
        } 

        // Check for <iq (3 chars)
        if (xml.regionMatches(true, firstTag, "<iq", 0, 3)) {
            return true;
        }

        // If it reaches here, it's either a protocol control element (like <r/> or <a/>)
        // or a stream-level tag (like <stream:features/>).
        return false;
    }
        
    /**
     * Determines if the provided XML string represents an XMPP Ping request.
     * <p>
     * This method uses low-level string matching to avoid the high overhead of 
     * full XML DOM parsing. It specifically targets XEP-0199 Ping requests 
     * which are always wrapped in an {@code <iq/>} stanza.
     * </p>
     *
     * @param xml The raw inbound XMPP string from the WebSocket frame.
     * @return {@code true} if the stanza is an IQ containing the Ping namespace.
     */
    public static boolean isPingStanza(String xml) {
        // 1. Safety check for null or empty payloads.
        if (xml == null || xml.isEmpty()) {
            return false;
        }

        // 2. Locate the first actual XML tag.
        // While XMPP over WebSockets usually lacks leading whitespace, 
        // we search for '<' to ensure robustness against malformed or padded streams.
        int firstTag = xml.indexOf('<');
        if (firstTag == -1) {
            return false;
        }

        /**
         * 3. Perform a case-insensitive region match for the IQ start tag.
         * * We use regionMatches instead of startsWith because:
         * - It handles potential leading whitespace (via firstTag offset).
         * - It is faster than regex or full XML parsing.
         * - XEP-0199 specifies pings MUST be sent via <iq/>.
         */
        if (xml.regionMatches(true, firstTag, "<iq", 0, 3)) {
            
            /**
             * 4. Verify the Ping Namespace (XEP-0199).
             * * We look for the "urn:xmpp:ping" string. While a full parser would 
             * verify the namespace is inside the 'xmlns' attribute, a simple 
             * contains() check is an acceptable performance trade-off for 
             * initial routing in the Netty pipeline.
             */
            return xml.contains("urn:xmpp:ping");
        }

        /**
         * 5. Fallback for non-IQ stanzas.
         * * If the tag is <message/>, <presence/>, or Stream Management elements 
         * (like <r/> or <a/>), it cannot be a standard XMPP Ping.
         */
        return false;
    }
    
    /**
     * Injects a server-generated XEP-0359 stanza-id extension into an outgoing XMPP <message> stanza.
     *
     * This method is primarily used for:
     * - Message Archive Management (MAM) correlation
     * - Stable message identification across devices
     * - Offline sync and deduplication
     * - Reliable message tracking in distributed systems
     *
     * The generated stanza-id is based on a monotonic ULID to ensure:
     * - Lexicographically sortable identifiers
     * - High uniqueness under concurrent load
     * - Time-ordered message indexing support
     *
     * @param xml    raw XMPP message XML string (must contain </message> closing tag)
     * @param domain XMPP domain used as the 'by' attribute in stanza-id (server identity)
     * @return XML message enriched with <stanza-id/> extension
     */
    public static String insertStanzaId(String xml, String ulidString, String domain) {
        /**
         * Construct XEP-0359 stanza-id extension element.
         *
         * Format:
         * <stanza-id xmlns='urn:xmpp:sid:0'
         *            by='domain.com'
         *            id='ulid'/>
         *
         * 'by'  → identifies the entity that generated the ID (server/domain)
         * 'id'  → globally unique message identifier
         */
        String stanzaIdExtension =
                "<stanza-id xmlns='urn:xmpp:sid:0' by='" +
                domain +
                "' id='" +
                ulidString +
                "'/>";

        /**
         * Standard XMPP message closing tag.
         * We inject stanza-id just before this closing tag.
         */
        String closingTag = "</message>";

        /**
         * Optimized fast-path:
         * If XML ends correctly with </message>, safely insert stanza-id
         * before the closing tag without full XML parsing.
         */
        if (xml.endsWith(closingTag)) {

            // Remove closing tag, append stanza-id, then re-attach closing tag
            return xml.substring(0, xml.length() - closingTag.length())
                    + stanzaIdExtension
                    + closingTag;

        } else {

            /**
             * Fallback path:
             * If XML is malformed, contains whitespace, or unexpected formatting,
             * attempt a replace-based injection.
             *
             * WARNING:
             * - Less safe than structured XML parsing
             * - Slightly slower
             * - Used only as a resilience fallback
             */
            return xml.replace(closingTag, stanzaIdExtension + closingTag);
        }
    }
}
package com.algomeet.xmpp.chatservice.util;

public class XmppCustomRetentionStanzaUtil {
	private static final String NS_CUSTOM_RETENTION_META = "urn:algomeet:retention:0";
    
    private static final String RETENTION_TAG = "<retention";
    
    private static final String DAYS_ATTR = "days='";
    private static final String DAYS_ATTR_DQ = "days=\""; // Handle double quotes just in case
		    
    public static boolean messageHasRetention(String xml) {

		// Locate the last occurrence of the <countable tag.
		// We use lastIndexOf because in typical XMPP stanzas this extension tag
		// appears near the end of the message payload, making backward search
		// potentially faster than scanning from the beginning.
		int tagIndex = xml.lastIndexOf(RETENTION_TAG);

		// If the tag is not present at all, we can safely exit early.
		if (tagIndex == -1) {
			return false;
		}

		// Find the closing '>' of the <countable ...> element starting from the tag position.
		// This defines the boundary of the tag we are inspecting.
		int end = xml.indexOf('>', tagIndex);

		// If no closing bracket is found, the XML is malformed or incomplete,
		// so we treat it as non-countable for safety.
		if (end == -1) {
			return false;
		}

		// Check that the expected namespace appears within the bounds of the tag.
		// This ensures we are not matching random occurrences elsewhere in the XML body.
		int nsIndex = xml.indexOf(NS_CUSTOM_RETENTION_META, tagIndex);

		// Valid only if the namespace exists AND is located inside the <countable ...> tag.
		// This prevents false positives from other parts of the stanza.
		return nsIndex != -1 && nsIndex < end;
	}
    
    public static String getMessageRetentionDays(String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }

        // 1. Locate the last occurrence of the <target tag
        int tagIndex = xml.lastIndexOf(RETENTION_TAG);
        if (tagIndex == -1) {
            return null;
        }

        // 2. Find the closing '>' of the <target ...> element
        int end = xml.indexOf('>', tagIndex);
        if (end == -1) {
            return null;
        }

        // 3. Verify the namespace is within the tag bounds
        int nsIndex = xml.indexOf(NS_CUSTOM_RETENTION_META, tagIndex);
        if (nsIndex == -1 || nsIndex >= end) {
            return null;
        }

        // 4. Look for id=' or id=" within the tag bounds
        int idAttrIndex = xml.indexOf(DAYS_ATTR, tagIndex);
        char quoteChar = '\'';
        
        // Fallback to double quotes if single quotes aren't used
        if (idAttrIndex == -1 || idAttrIndex >= end) {
            idAttrIndex = xml.indexOf(DAYS_ATTR_DQ, tagIndex);
            quoteChar = '"';
        }

        // If no ID attribute is found inside the tag bounds, exit
        if (idAttrIndex == -1 || idAttrIndex >= end) {
            return null;
        }

        // 5. Calculate the start and end of the actual ID value
        int idStart = idAttrIndex + 6; // Move past id=' or id="
        int idEnd = xml.indexOf(quoteChar, idStart);

        // Ensure the closing quote is also within the tag bounds
        if (idEnd == -1 || idEnd >= end) {
            return null;
        }

        return xml.substring(idStart, idEnd);
    }
}

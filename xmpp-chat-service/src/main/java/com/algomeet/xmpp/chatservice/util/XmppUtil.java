package com.algomeet.xmpp.chatservice.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

public class XmppUtil {
	private static final String DOMAIN_SEPARATOR = "@";
	
	public static String getUserKey(String fullJid) {
		if(!StringUtils.hasText(fullJid)) {
			return null;
		}
		
		return fullJid.split(DOMAIN_SEPARATOR, 2)[0];
	}
	
	public static String getRoomKey(String roomJid) {
		if(!StringUtils.hasText(roomJid)) {
			return null;
		}
		
		return roomJid.split(DOMAIN_SEPARATOR, 2)[0];
	}
	
	/**
     * Extracts an attribute value from a specific sub-tag.
     * Example: Extract 'id' from <received xmlns='...' id='msg_123'/>
     */
    public static String extractSubAttribute(String xml, String tagName, String attrName) {
        // Regex looks for: <tagName ... attrName=['"]VALUE['"]
        String patternString = "<" + tagName + "[^>]*\\s" + attrName + "=['\"]([^'\"]+)['\"]";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(xml);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

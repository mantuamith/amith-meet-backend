package com.algomeet.xmpp.chatservice.util;

import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
     * Simple regex or string manipulation to extract 'h' value
     */
    public static long parseHAttribute(String xml) {
        try {
            String hValue = xml.split("h='")[1].split("'")[0];
            return Long.parseLong(hValue);
        } catch (Exception e) {
        	log.error("Error parsing ack from client: {}", xml, e);
            return 0;
        }
    }
}

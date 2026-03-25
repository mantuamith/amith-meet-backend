package com.algomeet.xmpp.chatservice.util;

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
	
	
}

package com.algomeet.xmpp.chatservice.util;

import org.springframework.util.StringUtils;

public class XmppUtil {
	private static final String JID_AND_DOMAIN_SEPARATOR = "@";
	
	public static String getUserKey(String fullJid) {
		if(!StringUtils.hasText(fullJid)) {
			return null;
		}
		
		return fullJid.split(JID_AND_DOMAIN_SEPARATOR, 2)[0];
	}
}

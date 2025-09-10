package com.algomeet.notificationservice.util;

public class WebSocketMessageUtil {
	private static final String AUTH_MASSAGE_TYPE = "\"AUTHORIZATION\":";
	private static final String ACK_MASSAGE_TYPE = "\"TYPE\":\"ACK\"";
	
	
	public static boolean isAuthMessage(String payload) {
		return removeWhiteSpaces(payload).toUpperCase().contains(AUTH_MASSAGE_TYPE);
	}
	
	public static boolean isAckMessage(String payload) {
		return removeWhiteSpaces(payload).toUpperCase().contains(ACK_MASSAGE_TYPE);
	}
		
	private static String removeWhiteSpaces(String str) {
		return str.replaceAll(" ", "");
	}
}

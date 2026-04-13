package com.algomeet.xmpp.chatservice.util;

public class MucCountUtil {
	public static String composeMucCountSync(String from, String to, String roomId, int count) {
        return new StringBuilder(256) // Pre-size buffer to avoid resizing
            .append("<message from='").append(from).append("' ")
            .append("to='").append(to).append("' ")
            .append("type='headline'>")
            .append("<sync xmlns='urn:xmpp:algomeet:sync:unread'>")
            .append("<muc room_id='").append(roomId).append("' ")
            .append("unread_count='").append(count).append("' />")
            .append("</sync></message>")
            .toString();
    }
	
	public static String composeCountSync(String from, String to, String senderKey, int count) {
        return new StringBuilder(256) // Pre-size buffer to avoid resizing
            .append("<message from='").append(from).append("' ")
            .append("to='").append(to).append("' ")
            .append("type='headline'>")
            .append("<sync xmlns='urn:xmpp:algomeet:sync:unread'>")
            .append("<direct sender_key='").append(senderKey).append("' ")
            .append("unread_count='").append(count).append("' />")
            .append("</sync></message>")
            .toString();
    }
}

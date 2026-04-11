package com.algomeet.xmpp.chatservice.stanza;

public class XmppServerAckSender {
	private static final String NS = "urn:algomeet:xmpp:server-ack";

	/**
	 * Sends custom server-ack stanza after DB persistence success.
	 *
	 * @param ctx Netty channel context
	 * @param messageId original client message id
	 * @param toJid recipient JID
	 */
	public static String toXml(
			String messageId,
			String fromJid,
			String toJid
			) {
		
		return String.format(
				"<message from='%s' to='%s'>" +
						"<server-ack xmlns='%s' id='%s' status='success'/>" +
						"</message>",
						fromJid,
						toJid,
						NS,
						messageId
				);			
	}
}
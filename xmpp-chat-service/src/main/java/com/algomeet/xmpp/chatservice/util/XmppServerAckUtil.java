package com.algomeet.xmpp.chatservice.util;

import com.algomeet.xmpp.chatservice.stanza.XmppServerAckSender;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class XmppServerAckUtil {
	public static void send(
			ChannelHandlerContext ctx,
			String messageId,
			String fromJid,
			String stanzaId,
			Integer retentionDays) {
		// Send server ACK
		String xml = XmppServerAckSender.toXml(messageId, fromJid, stanzaId, retentionDays);
		
		ctx.writeAndFlush(new TextWebSocketFrame(xml));
	}

}

package com.algomeet.xmpp.chatservice.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class XmppStreamManagementUtil {
	public static void incrementAndSendInboundH(ChannelHandlerContext ctx) {
		// Acknowledge receipt to the sender (Server -> Client 'h' update)
    	AtomicBoolean isEnabledSm = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).get();
        AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();
        
        if (isEnabledSm != null && isEnabledSm.get() && handledCount != null) {
            long h = handledCount.incrementAndGet();
            ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(h).toXml()));
        }
	}

}

package com.algomeet.xmpp.chatservice.routing.handler;

import org.springframework.stereotype.Component;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

@Component
public class ServerQueryHandler {
	 public void handleQuery(ChannelHandlerContext ctx, String xml) {
	        if (xml.contains("disco#info")) {
	            String res = "<iq type='result' from='plays.shakespeare.lit' id='info1'>" +
	                         "<query xmlns='http://jabber.org/protocol/disco#info'/></iq>";
	            ctx.writeAndFlush(new TextWebSocketFrame(res));
	        }
	    }
}

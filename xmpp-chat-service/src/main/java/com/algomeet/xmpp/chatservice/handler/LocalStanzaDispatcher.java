package com.algomeet.xmpp.chatservice.handler;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.session.XmppSessionManager;

import io.netty.channel.Channel;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class LocalStanzaDispatcher {
	private final XmppSessionManager sessionManager; 
	
	 public void handleRouting(String to, String from, String id, String originalXml) {
	        Channel targetChannel = sessionManager.getChannel(to);

	        if (targetChannel != null && targetChannel.isActive()) {
	            targetChannel.writeAndFlush(new TextWebSocketFrame(originalXml));
	        }
	        /*
	        else {
	            String error = String.format(
	                "<iq type='error' to='%s' id='%s' from='server'>" +
	                "<error type='cancel'><service-unavailable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/></error></iq>",
	                from, id);
	            ctx.channel().writeAndFlush(new TextWebSocketFrame(error));
	        } */
	    }
}

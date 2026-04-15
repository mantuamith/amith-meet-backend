package com.algomeet.xmpp.chatservice.protocol.ping;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * XEP-0199: XMPP Ping Handler
 *
 * Responsibilities:
 * 1. Detects inbound Ping requests (<ping xmlns='urn:xmpp:ping'/>)
 * 2. Short-circuits the pipeline to return an IQ-Result (Pong)
 * 3. Prevents unnecessary downstream processing for simple heartbeats
 *
 * IMPORTANT:
 * - This operates at the APPLICATION LAYER.
 * - The response generated here will be tracked by XEP-0198 Stream Management 
 * if SM is enabled, as the "Pong" is a valid <iq/> stanza.
 */
@Slf4j
@ChannelHandler.Sharable
@RequiredArgsConstructor
@Component
public class XmppPingHandler extends ChannelDuplexHandler {
	private final DomainProperties domainProperties;
    /**
     * Intercepts inbound traffic to check for Ping requests.
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof TextWebSocketFrame)) {
            super.channelRead(ctx, msg);
            return;
        }

        String xml = ((TextWebSocketFrame) msg).text();

        // Check if the stanza is an IQ-Get Ping
        if (XmppStanzaUtil.isPingStanza(xml)) {
            handlePing(ctx, xml);
            // We do NOT call super.channelRead(ctx, msg) because we have handled the stanza.
            // This prevents the Ping from reaching deeper business logic/DB routers.
            return;
        }

        super.channelRead(ctx, msg);
    }

    /**
     * Constructs and sends the XMPP Pong (IQ Result).
     * * @param ctx The Netty context to write the response.
     * @param xml The original inbound Ping XML to extract JIDs and ID.
     */
    private void handlePing(ChannelHandlerContext ctx, String xml) {
        String id = XmppStanzaUtil.getAttribute(xml, "id");
        String to = XmppStanzaUtil.getAttribute(xml, "to");
        
        // 1. Try to get 'from' from the XML
        String from = XmppStanzaUtil.getAttribute(xml, "from");
        
        // 2. Fallback: Get the authenticated JID from the session if 'from' is missing
        if (from == null) {
        	XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
        	from = principal.getBareJid();
        }

        String pong = String.format(
            "<iq from='%s' to='%s' id='%s' type='result'/>",
            to != null ? to : domainProperties.getDomain(),
            from, // Now guaranteed to be a valid JID
            id
        );
        
        ctx.writeAndFlush(new TextWebSocketFrame(pong));
    }
}
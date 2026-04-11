package com.algomeet.xmpp.chatservice.sm;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.properties.XmppSmRedisProperties;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppSmRedisUtil;
import com.algomeet.xmpp.chatservice.util.XmppSmUtil;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * XEP-0198 Stream Management Handler
 *
 * Responsibilities:
 * 1. Tracks inbound stanzas at transport level
 * 2. Maintains SM sequence counter (h value)
 * 3. Sends cumulative acknowledgements (<a h='N'/>)
 *
 * IMPORTANT:
 * - This operates at TRANSPORT LAYER only
 * - It does NOT depend on DB success or business logic
 * - It MUST run before application-level processing
 */
@Slf4j
@ChannelHandler.Sharable
@RequiredArgsConstructor
@Component
public class XmppStreamManagementHandler extends ChannelDuplexHandler {
	private final XmppSmUtil xmppSmUtil;
	
    /**
     * Intercepts all inbound XMPP traffic before it reaches business handlers.
     *
     * For every valid XMPP stanza:
     * - Determines whether it should be counted in SM sequence
     * - If SM is enabled, increments inbound counter (h)
     *
     * NOTE:
     * - SM count is cumulative and ordered
     * - It is independent of processing success/failure downstream
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    	TextWebSocketFrame frame = (TextWebSocketFrame) msg;
    	String xml = frame.text();

        // Only XMPP stanzas (<message/>, <iq/>, <presence/>) are part of SM tracking
        // Control frames like <r/>, <a/>, whitespace keep-alives are NOT counted
        if (XmppStanzaUtil.isCountableStanza(xml)) {
            // SM must be explicitly enabled for the session (<enable xmlns='urn:xmpp:sm:3'/>)
            AtomicBoolean isEnabledSM =
                    ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).get();

            // If SM is active, we increment the inbound sequence counter (h)
            if (isEnabledSM != null && isEnabledSM.get()) {
            	
                /**
                 * Increment SM counter and optionally trigger ACK logic.
                 *
                 * This represents:
                 * "Server has successfully received one more stanza from client"
                 *
                 * IMPORTANT:
                 * - This does NOT mean DB persistence succeeded
                 * - This does NOT mean message was delivered to recipient
                 * - This is ONLY transport-level acknowledgment tracking
                 */
             	xmppSmUtil.incrementAndSendInboundH(ctx);        
            }
        }

        // Continue pipeline to downstream handlers (chat routing, persistence, etc.)
        super.channelRead(ctx, msg);
    }
}
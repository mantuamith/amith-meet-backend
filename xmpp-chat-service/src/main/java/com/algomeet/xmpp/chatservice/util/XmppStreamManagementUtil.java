package com.algomeet.xmpp.chatservice.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * Utility class for managing XEP-0198 Stream Management state and signaling.
 * <p>
 * This utility handles the logic for tracking handled stanzas and generating 
 * acknowledgment ({@code <a h='...'/>}) stanzas. These mechanisms are vital for 
 * detecting transport-layer failures and enabling stream resumption without data loss.
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.0
 */
public class XmppStreamManagementUtil {

    /**
     * Increments the server-side inbound handled counter and transmits an 
     * acknowledgment stanza to the client.
     * <p>
     * <b>Logic Flow:</b>
     * <ol>
     * <li>Checks if Stream Management is enabled for the current channel.</li>
     * <li>Atomically increments the {@code h} (handled) counter.</li>
     * <li>Sends a {@link StreamAck} back to the client, allowing the client to 
     * clear its retransmission buffer.</li>
     * </ol>
     * </p>
     *
     * @param ctx The {@link ChannelHandlerContext} for the active Netty session.
     */
    public static void incrementAndSendInboundH(ChannelHandlerContext ctx) {
        // Retrieve the Stream Management status and counter from the channel attributes
        AtomicBoolean isEnabledSm = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).get();
        AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();
        
        // Only process if SM is explicitly enabled and the counter is initialized
        if (isEnabledSm != null && isEnabledSm.get() && handledCount != null) {
            
            // Increment the counter and get the new value (representing stanzas handled by server)
            long h = handledCount.incrementAndGet();
            
            // Generate the <a xmlns='urn:xmpp:sm:3' h='...'/> stanza and flush to the WebSocket
            ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(h).toXml()));
        }
    }
}
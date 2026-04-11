package com.algomeet.xmpp.chatservice.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for managing XEP-0198 Stream Management state and signaling.
 *
 * Handles:
 * - Incrementing inbound handled counter (h)
 * - Sending <a h='N'/> acknowledgements
 * - Retrieving current SM state
 */
@Slf4j
public class XmppSmUtil {

    /**
     * Increments inbound handled counter (h) and sends ACK.
     */
    public static void incrementAndSendInboundH(ChannelHandlerContext ctx) {
        ctx.executor().execute(() -> {
            AtomicBoolean isEnabled = ctx.channel()
                    .attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).get();

            AtomicLong handledCount = ctx.channel()
                    .attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();

            if (isEnabled != null && isEnabled.get() && handledCount != null) {

                long h = handledCount.incrementAndGet();
                log.debug("Acknowledging stanza h={}", h);

                ctx.writeAndFlush(
                        new TextWebSocketFrame(new StreamAck(h).toXml())
                );
            }
        });
    }

    /**
     * Retrieves the current inbound handled counter (h).
     *
     * @param ctx Netty channel context
     * @return current value of h, or 0 if not initialized
     */
    public static long getInboundH(ChannelHandlerContext ctx) {
        AtomicLong handledCount = ctx.channel()
                .attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();

        if (handledCount == null) {
            return 0L;
        }

        return handledCount.get();
    }
}
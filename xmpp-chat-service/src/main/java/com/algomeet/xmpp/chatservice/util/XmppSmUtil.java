package com.algomeet.xmpp.chatservice.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * XEP-0198 Stream Management Utility
 * -----------------------------------
 *
 * This class manages Stream Management (SM) state for XMPP connections.
 *
 * Responsibilities:
 * <ul>
 *   <li>Maintain inbound handled counter (h)</li>
 *   <li>Send cumulative acknowledgements (<a h='N'/>)</li>
 *   <li>Persist SM state for session resumption (Redis-backed)</li>
 *   <li>Expose current SM sequence state for debugging/resume</li>
 * </ul>
 *
 * IMPORTANT ARCHITECTURE RULES:
 * <ul>
 *   <li>SM ACK is TRANSPORT LAYER ONLY (NOT business-level success)</li>
 *   <li>ACK does NOT guarantee DB persistence or message delivery</li>
 *   <li>h is strictly monotonically increasing per connection</li>
 *   <li>This class must be thread-safe per Netty channel</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppSmUtil {

    /**
     * Redis utility used for persisting last acknowledged SM state
     * for session resumption (XEP-0198 resume support).
     */
    private final XmppSmSessionRedisUtil xmppSmRedisUtil;

    /**
     * Increments the inbound SM handled counter (h) and sends a cumulative ACK.
     *
     * <p>
     * Flow:
     * <ol>
     *   <li>Verify Stream Management is enabled for the session</li>
     *   <li>Increment local handled counter (h)</li>
     *   <li>Persist h to Redis (if session is resumable)</li>
     *   <li>Send <a h='N'/> back to client via WebSocket</li>
     * </ol>
     * </p>
     *
     * <p>
     * ⚠ IMPORTANT:
     * - This ACK is transport-level only
     * - It does NOT reflect message processing success
     * - It may be sent before DB persistence completes
     * </p>
     *
     * @param ctx Netty channel context for active XMPP session
     */
    public void incrementAndSendInboundH(ChannelHandlerContext ctx) {

        // Ensure execution is tied to Netty EventLoop (thread-safe per channel)
        ctx.executor().execute(() -> {

            // Check if Stream Management is enabled (<enable xmlns='urn:xmpp:sm:3'/>)
            AtomicBoolean isEnabled = ctx.channel()
                    .attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY)
                    .get();

            // Local per-session handled counter (h)
            AtomicLong handledCount = ctx.channel()
                    .attr(XmppSessionAttributes.SM_INBOUND_H_KEY)
                    .get();

            if (isEnabled != null && isEnabled.get() && handledCount != null) {

                // Increment SM sequence (monotonic counter)
                long h = handledCount.incrementAndGet();

                log.debug("SM ACK generated: h={}", h);                

                /**
                 * Send XEP-0198 ACK to client:
                 *
                 * <a xmlns='urn:xmpp:sm:3' h='N'/>
                 *
                 * This tells the client:
                 * - Server has received all stanzas up to h
                 * - Client can safely drop its resend buffer
                 */
                ctx.writeAndFlush(
                        new TextWebSocketFrame(new StreamAck(h).toXml())
                );
            }
        });
    }

    /**
     * Retrieves the current inbound SM handled counter (h).
     *
     * <p>
     * This value represents:
     * - Number of stanzas received from client
     * - Transport-level delivery acknowledgment state
     * </p>
     *
     * @param ctx Netty channel context
     * @return current SM handled counter (h), or 0 if not initialized
     */
    public static long getInboundH(ChannelHandlerContext ctx) {

        AtomicLong handledCount = ctx.channel()
                .attr(XmppSessionAttributes.SM_INBOUND_H_KEY)
                .get();

        return handledCount != null ? handledCount.get() : 0L;
    }
}
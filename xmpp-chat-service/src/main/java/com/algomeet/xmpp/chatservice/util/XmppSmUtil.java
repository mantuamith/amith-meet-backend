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
 * XMPP Stream Management Utility (XEP-0198)
 * ==========================================================
 *
 * Purpose:
 * ----------------------------------------------------------
 * Stream Management adds reliability to XMPP streams by
 * allowing both client and server to track stanza flow.
 *
 * This utility manages the SERVER SIDE inbound counter (h)
 * and sends acknowledgements back to the client.
 *
 * Core Responsibilities:
 * ----------------------------------------------------------
 * 1. Maintain inbound handled counter (h)
 * 2. Send cumulative acknowledgements <a h='N'/>
 * 3. Expose current counter for persistence/resume
 * 4. Keep operations safe inside Netty channel threading model
 *
 * What "h" Means:
 * ----------------------------------------------------------
 * h = number of inbound stanzas successfully RECEIVED by
 * the server transport stream.
 *
 * Example:
 * Client sends 5 messages
 * Server receives all 5
 * Server may send:
 *
 * <a xmlns='urn:xmpp:sm:3' h='5'/>
 *
 * Important Distinction:
 * ----------------------------------------------------------
 * This is NOT business success confirmation.
 *
 * ACK means:
 *   ✔ bytes/frame/stanza accepted by stream
 *
 * ACK does NOT mean:
 *   ✘ saved to database
 *   ✘ delivered to recipient
 *   ✘ push notification sent
 *   ✘ business transaction completed
 *
 * Threading Model:
 * ----------------------------------------------------------
 * Netty channels are safest when mutated from their assigned
 * EventLoop thread. This class ensures writes/counter updates
 * happen inside that execution context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppSmUtil {

    /**
     * Increment inbound handled counter and immediately send
     * Stream Management acknowledgement to client.
     *
     * Triggered when:
     * ------------------------------------------------------
     * Server successfully accepts an inbound stanza that
     * should advance the XEP-0198 receive counter.
     *
     * Internal Steps:
     * ------------------------------------------------------
     * 1. Switch execution to channel EventLoop thread
     * 2. Verify Stream Management is enabled
     * 3. Increment local h counter
     * 4. Send <a h='N'/> to client
     *
     * Why cumulative ACK?
     * ------------------------------------------------------
     * ACK is cumulative, not per-message.
     *
     * Example:
     * If h=100, client knows stanzas 1..100 are received.
     *
     * @param ctx active Netty channel context
     */
    public void incrementAndSendInboundH(ChannelHandlerContext ctx) {

        /**
         * Execute inside the channel's assigned EventLoop.
         *
         * This avoids race conditions caused by external threads
         * mutating channel state or writing concurrently.
         */
        ctx.executor().execute(() -> {

            /**
             * Indicates whether Stream Management was enabled
             * for this connection using:
             *
             * <enable xmlns='urn:xmpp:sm:3'/>
             */
            AtomicBoolean isEnabled = ctx.channel()
                    .attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY)
                    .get();

            /**
             * Per-channel atomic inbound counter.
             *
             * Stored in Netty channel attributes so it remains
             * bound to this connection lifecycle.
             */
            AtomicLong handledCount = ctx.channel()
                    .attr(XmppSessionAttributes.SM_INBOUND_H_KEY)
                    .get();

            /**
             * Only process acknowledgements if:
             * - SM is enabled
             * - Counter exists
             */
            if (isEnabled != null
                    && isEnabled.get()
                    && handledCount != null) {

                /**
                 * Increment monotonically.
                 *
                 * h must only move forward:
                 * 1,2,3,4...
                 *
                 * Never decrement or reuse values.
                 */
                long h = handledCount.incrementAndGet();

                log.debug("SM ACK generated: h={}", h);

                /**
                 * Construct XEP-0198 acknowledgement stanza:
                 *
                 * <a xmlns='urn:xmpp:sm:3' h='N'/>
                 *
                 * Meaning to client:
                 * ------------------------------------------
                 * Server confirms receipt of all inbound
                 * stanzas up to sequence N.
                 *
                 * Client may safely discard local resend
                 * buffer entries <= N.
                 */
                ctx.writeAndFlush(
                        new TextWebSocketFrame(
                                new StreamAck(h).toXml()
                        )
                );
            }
        });
    }

    /**
     * Returns current inbound handled counter.
     *
     * Common Uses:
     * ------------------------------------------------------
     * 1. Save session state before disconnect
     * 2. Persist resume checkpoint to Redis
     * 3. Diagnostics / debugging
     * 4. Generate <resumed h='N'/>
     *
     * If counter does not exist:
     * - SM not enabled yet
     * - channel not initialized
     * - state already cleaned
     *
     * @param ctx Netty channel context
     * @return current h value, or 0 if absent
     */
    public static long getInboundH(ChannelHandlerContext ctx) {

        /**
         * Fetch channel-local atomic counter.
         */
        AtomicLong handledCount = ctx.channel()
                .attr(XmppSessionAttributes.SM_INBOUND_H_KEY)
                .get();

        /**
         * Safe fallback to zero when missing.
         */
        return handledCount != null
                ? handledCount.get()
                : 0L;
    }
}
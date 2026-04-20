package com.algomeet.xmpp.chatservice.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;

import io.netty.channel.ChannelHandlerContext;

public class XmppSmSessionUtil {

    /**
     * Initializes XEP-0198 Stream Management session attributes.
     *
     * Responsibilities:
     * - Enables SM inbound tracking
     * - Stores resumable preference
     * - Stores SM session ID (previd)
     *
     * @param ctx Netty channel context
     * @param resumeRequested whether client requested resume support
     * @param smId session identifier (previd), may be null
     */
    public static void initSmSession(ChannelHandlerContext ctx,
                                     boolean resumeRequested,
                                     String smId,
                                     Long h) {
    	// Initialize SM Inbound H
    	ctx.channel()
    	.attr(XmppSessionAttributes.SM_INBOUND_H_KEY)
    	.set(new AtomicLong(h));  
    	
        // Enable inbound SM tracking (h counter)
        ctx.channel()
           .attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY)
           .set(new AtomicBoolean(true));

        // Store whether this session supports resumption
        ctx.channel()
           .attr(XmppSessionAttributes.SM_RESUMABLE_KEY)
           .set(new AtomicBoolean(resumeRequested));

        // Store SM session ID (previd) if provided
        if (smId != null) {
            ctx.channel()
               .attr(XmppSessionAttributes.SM_ID_KEY)
               .set(smId);
        }
        

        /**
         * Marks the current Netty channel session as successfully resumed
         * under Stream Management (XEP-0198).
         *
         * This indicates that the client has reconnected using a valid
         * previous SM session (previd) and the server has accepted the
         * resumption request.
         *
         * Effects of setting this flag:
         * - Enables replay continuation of buffered stanzas (if any)
         * - Differentiates resumed session from a fresh login session
         * - Helps prevent duplicate processing of previously acknowledged stanzas
         *
         * Note:
         * This is stored as a channel-level attribute and is only valid
         * for the lifetime of the active connection.
         */
        ctx.channel()
            .attr(XmppSessionAttributes.SM_RESUMPTION_SUCCESS_KEY)
            .set(new AtomicBoolean(true));
    }
}
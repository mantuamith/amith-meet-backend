package com.algomeet.xmpp.chatservice.util;

import java.util.concurrent.atomic.AtomicBoolean;

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
                                     String smId) {

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
    }
}
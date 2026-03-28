package com.algomeet.xmpp.chatservice.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.concurrent.atomic.AtomicLong;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;

/**
 * <p>Static registry of {@link AttributeKey} constants used to store and retrieve 
 * session-specific state within a Netty {@link Channel}.</p>
 * * <p>These attributes are essential for maintaining the state of a long-lived 
 * XMPP-over-WebSocket connection, allowing various handlers in the pipeline to 
 * access authentication data and protocol counters.</p>
 * * @author Algomeet Core Team
 */
public class XmppSessionAttributes {

    /**
     * Stores the authenticated {@link XmppPrincipal} for the session.
     * This is typically set by the {@code WebSocketPreAuthHandler} and used 
     * by the {@code XmppRoutingHandler} to identify the sender.
     */
    public static final AttributeKey<XmppPrincipal> PRINCIPAL = AttributeKey.valueOf("xmpp.principal");	

    /**
     * <p>The <b>Inbound</b> stanza counter ({@code h}) for XEP-0198 Stream Management.</p>
     * * <p>This tracks how many stanzas the <b>server</b> has received from the client. 
     * It is incremented every time a valid inbound stanza is processed.</p>
     */
    public static final AttributeKey<AtomicLong> SM_INBOUND_H_KEY = 
            AttributeKey.valueOf("smInboundH");

    /**
     * <p>The <b>Outbound</b> stanza counter ({@code h}) for XEP-0198 Stream Management.</p>
     * * <p>This tracks how many stanzas the <b>server</b> has sent to the client. 
     * This value is included in server-to-client acknowledgments and used to 
     * manage the retransmission buffer.</p>
     */
    public static final AttributeKey<AtomicLong> SM_OUTBOUND_H_KEY = 
            AttributeKey.valueOf("smOutboundH");

    /**
     * Utility method to safely retrieve the authenticated principal from a channel.
     * * @param channel The active Netty channel.
     * @return The {@link XmppPrincipal} associated with this channel, or {@code null} 
     * if the channel is not yet authenticated.
     */
    public static XmppPrincipal getPrincipal(Channel channel) {
        return channel.attr(PRINCIPAL).get();
    }
    
    public static final AttributeKey<Boolean> INITIAL_PRESENCE_SENT = AttributeKey.valueOf("initial_presence_sent");
}
package com.algomeet.xmpp.chatservice.session.constant;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * <p>Static registry of {@link AttributeKey} constants used to store and retrieve 
 * session-specific state within a Netty {@link Channel}.</p>
 *
 * <p>These attributes are essential for maintaining the state of a long-lived 
 * XMPP-over-WebSocket connection, allowing various handlers in the pipeline to 
 * access authentication data and protocol counters across different Netty event loops.</p>
 *
 * @author Algomeet Core Team
 * @version 1.1
 */
public class XmppSessionAttributes {

    /**
     * Stores the authenticated {@link XmppPrincipal} for the session.
     * <p>This is typically set by the {@code WebSocketPreAuthHandler} and used 
     * by the {@code XmppRoutingHandler} to identify the sender of every stanza.</p>
     */
    public static final AttributeKey<XmppPrincipal> PRINCIPAL = AttributeKey.valueOf("xmpp.principal");	

    /**
     * <p>The <b>Inbound</b> stanza counter ({@code h}) for XEP-0198 Stream Management.</p>
     * <p>This tracks how many stanzas the <b>server</b> has received from the client. 
     * It is incremented every time a valid inbound stanza is processed and reported 
     * back to the client via {@code <a h='...'/>} stanzas.</p>
     */
    public static final AttributeKey<AtomicLong> SM_INBOUND_H_KEY = 
            AttributeKey.valueOf("smInboundH");
    
    /**
     * Flag indicating if Stream Management (XEP-0198) is enabled for inbound stanza counting.
     */
    public static final AttributeKey<AtomicBoolean> SM_INBOUND_H_ENABLED_KEY = 
            AttributeKey.valueOf("smInboundHEnabled");

    /**
     * <p>The <b>Outbound</b> stanza counter ({@code h}) for XEP-0198 Stream Management.</p>
     * <p>This tracks how many stanzas the <b>server</b> has sent to the client. 
     * This value is used to manage the server-side retransmission buffer and is 
     * synchronized with client acknowledgments.</p>
     */
    public static final AttributeKey<AtomicLong> SM_OUTBOUND_H_KEY = 
            AttributeKey.valueOf("smOutboundH");
    
    /**
     * Flag indicating if Stream Management (XEP-0198) is enabled for outbound stanza counting.
     */
    public static final AttributeKey<AtomicBoolean> SM_OUTBOUND_H_ENABLED_KEY = 
            AttributeKey.valueOf("smOutboundHEnabled");

    /**
     * Tracks whether the initial presence stanza has been broadcasted for this session.
     * <p>Used to prevent duplicate presence broadcasts and to trigger MAM (Message 
     * Archive Management) catch-up logic upon first connection.</p>
     */
    public static final AttributeKey<Boolean> IS_INITIAL_PRESENCE_SENT = AttributeKey.valueOf("initial_presence_sent");
    
    /**
     * Flag indicating if the current session is resumable (XEP-0198).
     * <p>If true, the server will buffer outbound stanzas for a grace period 
     * after a disconnect, allowing the client to resume the stream without data loss.</p>
     */
    public static final AttributeKey<AtomicBoolean> SM_RESUMABLE_KEY = 
            AttributeKey.valueOf("smResumable");
    
    /**
     * The unique Stream Management Identifier (SMID) assigned to this session.
     * <p>This ID is used by the client during a {@code <resume/>} attempt to identify 
     * the previous stream state in the {@code UserSessionRegistry}.</p>
     */
    public static final AttributeKey<String> SM_ID_KEY = 
            AttributeKey.valueOf("smId");
    
    /**
     * Netty Channel attribute key indicating whether Stream Management (XEP-0198)
     * session resumption was successfully completed.
     *
     * This flag is set to TRUE when:
     * - the client successfully resumes a previous SM session (using 'previd')
     * - server confirms continuation of the existing stream state
     *
     * It is used to:
     * - distinguish resumed sessions from fresh logins
     * - control replay behavior of buffered stanzas
     * - prevent duplicate delivery after successful resumption
     *
     * Value type:
     * - AtomicBoolean (mutable per-channel state indicator)
     */
    public static final AttributeKey<AtomicBoolean> SM_RESUMPTION_SUCCESS_KEY = 
            AttributeKey.valueOf("smResumptionSuccess");

    /**
     * Utility method to safely retrieve the authenticated principal from a channel.
     *
     * @param channel The active Netty channel.
     * @return The {@link XmppPrincipal} associated with this channel, or {@code null} 
     * if the channel is not yet authenticated.
     */
    public static XmppPrincipal getPrincipal(Channel channel) {
        return channel.attr(PRINCIPAL).get();
    }   
}
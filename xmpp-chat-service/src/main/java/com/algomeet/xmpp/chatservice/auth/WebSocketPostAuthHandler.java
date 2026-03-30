package com.algomeet.xmpp.chatservice.auth;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.XmppSessionManager;
import com.algomeet.xmpp.chatservice.stanza.BindResult;
import com.algomeet.xmpp.chatservice.session.XmppStreamAckTracker;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Finalizes the XMPP-over-WebSocket setup immediately after a successful 
 * WebSocket upgrade/handshake.</p>
 * 
 * <p>This handler is responsible for transitioning a connection from a raw 
 * WebSocket to a tracked, authenticated XMPP session. It performs the following 
 * critical bootstrap steps:</p>
 * <ul>
 *     <li>Initializes Stream Management (XEP-0198) counters (Inbound/Outbound 'h').</li>
 *     <li>Registers the session in the {@link XmppSessionManager} for routing.</li>
 *     <li>Updates the {@link UserSessionRegistry} to mark the user as ACTIVE.</li>
 *     <li>Initializes the {@link XmppStreamAckTracker} for reliable delivery.</li>
 *     <li>Attaches a lifecycle listener to ensure clean resource teardown on disconnect.</li>
 * </ul>
 * 
 * <p>Note: This handler is {@code @Sharable} as it does not maintain per-channel state; 
 * all state is stored within Channel Attributes or external Managers.</p>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketPostAuthHandler extends ChannelInboundHandlerAdapter {

    private final UserSessionRegistry userSessionRegistry;
    private final XmppSessionManager xmppSessionManager;
    private final XmppStreamAckTracker xmppStreamAckTracker;
    
    /**
     * Listens for Netty user events, specifically catching {@link WebSocketServerProtocolHandler.HandshakeComplete} 
     * to trigger the XMPP session activation logic.
     * 
     * @param ctx The channel context.
     * @param evt The event triggered in the pipeline.
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // Retrieve the principal established during the initial Auth/Upgrade phase
            XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
            
            if (principal != null) {
                String userKey = principal.getUserKey();
                String sessionId = principal.getSessionId();
               
                // 1. Initialize Stream Management Counters (XEP-0198)
                // These counters are attached to the channel attribute for thread-safe access
                ctx.channel().attr(XmppSessionAttributes.SM_OUTBOUND_H_KEY).set(new AtomicLong(0));
                // Initialize Initial Presence flag to false
                ctx.channel().attr(XmppSessionAttributes.INITIAL_PRESENCE_SENT).set(false);

                // 2. Register in Managers (Stateful registration)
                xmppSessionManager.register(userKey, ctx.channel());
                userSessionRegistry.addSession(userKey, new UserSession(sessionId, UserState.ACTIVE, Instant.now().toEpochMilli()));
                xmppStreamAckTracker.register(userKey);
                
                // 3. Setup Lifecycle Cleanup Listener
                // Ensures that if the client disappears, we unregister from all trackers
                ctx.channel().closeFuture().addListener((ChannelFutureListener) future -> {
                    xmppSessionManager.unregister(userKey);
                    userSessionRegistry.removeSession(userKey, sessionId);
                    xmppStreamAckTracker.unregister(userKey);
                    
                    log.info("XMPP Session {} for user {} cleaned up following channel close.", sessionId, userKey);
                });

                // 4. Send Bind Result (Confirmation of session establishment)
                // This informs the client of their full JID and the assigned Session ID
                ctx.channel().writeAndFlush(new TextWebSocketFrame(
                    new BindResult(principal.getFullJid(), sessionId).toXml()
                ));

                log.info("WebSocket Handshake Complete. User {} is now ACTIVE with Session ID: {}.", userKey, sessionId);
            } else {
                log.warn("WebSocket handshake completed but no XmppPrincipal found. Closing channel.");
                ctx.close();
            }
        }
        
        // Pass the event forward in case other handlers need to react
        super.userEventTriggered(ctx, evt);
    }
}
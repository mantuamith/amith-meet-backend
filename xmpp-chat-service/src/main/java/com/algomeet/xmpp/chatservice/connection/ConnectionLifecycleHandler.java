package com.algomeet.xmpp.chatservice.connection;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.connection.registry.LocalChannelRegistry;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.state.XmppBroadcastUserPresenceHandler;
import com.algomeet.xmpp.chatservice.service.CallTrackerService;
import com.algomeet.xmpp.chatservice.service.XmppSmBufferService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.BindResult;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Orchestrates the lifecycle of XMPP sessions over WebSocket connections.</p>
 *
 * <p>This handler is responsible for transitioning a raw Netty channel into a fully authenticated 
 * and managed XMPP stream. It handles the critical "Handshake-to-Bind" phase, initializes 
 * XEP-0198 Stream Management state, and ensures distributed session cleanup upon disconnection.</p>
 *
 * @author Algomeet Core Team
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ConnectionLifecycleHandler {

    private final UserSessionRegistry userSessionRegistry;
    private final LocalChannelRegistry localChannelRegistry;
    private final CallTrackerService callTrackerService;
    private final DomainProperties domainProperties;
	private final XmppBroadcastUserPresenceHandler xmppBroadcastUserPresenceHandler;
	private final XmppSmBufferService xmppSmBufferService;

    /**
     * <p>Finalizes the session establishment process after a successful WebSocket handshake 
     * and SASL authentication.</p>
     * * <p><b>Execution Flow:</b></p>
     * <ol>
     * <li>Attaches XEP-0198 counters to the {@link io.netty.channel.Channel} attributes.</li>
     * <li>Registers the user in the local {@code LocalChannelRegistry} for packet routing.</li>
     * <li>Persists the session metadata in the global {@code UserSessionRegistry} (Redis).</li>
     * <li>Transmits the {@code <bind/>} result to the client to confirm the full JID.</li>
     * </ol>
     *
     * @param ctx       The Netty channel context for the current connection.
     * @param principal The authenticated identity containing the User Key and Session ID.
     */
    public void connected(ChannelHandlerContext ctx, XmppPrincipal principal) {
        if (principal != null) {
            String userKey = principal.getUserKey();
            String sessionId = principal.getSessionId();

            // 1. Initialize Stream Management Counters (XEP-0198)
            // These counters are attached to the channel attribute for thread-safe access     
            ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).set(new AtomicLong(0));            
            // Initialize Initial Presence flag to false
            ctx.channel().attr(XmppSessionAttributes.IS_INITIAL_PRESENCE_SENT).set(false);

            // 2. Register in Local Channel Registry (Stateful registration)
            localChannelRegistry.register(userKey, ctx.channel());
            userSessionRegistry.addSession(userKey, new UserSession(sessionId, UserState.ACTIVE, Instant.now().toEpochMilli()));

            // 3. Send Bind Result (Confirmation of session establishment)
            // This informs the client of their full JID and the assigned Session ID
            ctx.channel().writeAndFlush(new TextWebSocketFrame(
                    new BindResult(principal.getFullJid(), sessionId, domainProperties.getDomain(), domainProperties.getGroupChatDomain()).toXml()
            ));

            log.info("WebSocket Handshake Complete. User {} is now ACTIVE with Session ID: {}.", userKey, sessionId);
        }
    }

    /**
     * <p>Handles the graceful or forced teardown of an XMPP session.</p>
     * * <p>This method performs state cleanup across local and distributed registries to prevent 
     * "zombie" sessions and ensures that any active WebRTC calls or signaling states are reconciled.</p>
     *
     * @param ctx       The Netty channel context being closed.
     * @param principal The identity associated with the disconnecting channel.
     */
    public void disconnected(ChannelHandlerContext ctx, XmppPrincipal principal) {
        if (principal != null) {
            String userKey = principal.getUserKey();
            String sessionId = principal.getSessionId();
            
            // Kick-in SM buffer for resume session messages
            xmppSmBufferService.save(ctx, principal)
            .doOnError(e -> log.error("Failed to save SM buffer for user {}: {}", userKey, e.getMessage()))
            .doFinally(signalType -> {
                // Log the signal type for better debugging (Cancel, Error, or Complete)
                log.debug("Finalizing session for {} with signal: {}", userKey, signalType);
                
                // Execute each cleanup task safely 
                safeExecute(
                    () -> localChannelRegistry.unregister(userKey), 
                    "Local Channel Registry", 
                    userKey
                );
            })
            .subscribe();
            

            log.info("Starting cleanup for session {} (User: {})", sessionId, userKey);
                        
            // Execute each cleanup task safely to ensure one failure doesn't block the entire teardown
             safeExecute(() -> userSessionRegistry.removeSession(userKey, sessionId), "User Session Registry", userKey);

            // Handle ongoing dropped calls.
            callTrackerService.handleTransportDrop(sessionId).subscribe();
            
            // Broadcast user presence GONE
            xmppBroadcastUserPresenceHandler.broadcastUserPresenceAsync(ctx, principal, UserState.GONE);

            log.info("Cleanup completed for session {}", sessionId);
        }
    }

    /**
     * Wraps cleanup tasks in a try-catch block to prevent partial cleanup failures 
     * from interrupting the session destruction sequence.
     *
     * @param action        The cleanup logic to execute.
     * @param componentName The name of the service being cleaned (for logging).
     * @param userKey       The identifier of the user for context.
     */
    private void safeExecute(Runnable action, String componentName, String userKey) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Failed to clean up {} for user {}: {}", componentName, userKey, ex.getMessage());
        }
    }
}
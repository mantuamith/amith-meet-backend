package com.algomeet.xmpp.chatservice.session;

import io.netty.channel.Channel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * <p>Manages the physical lifecycle of active Netty {@link Channel} connections 
 * on the local server instance.</p>
 * * <p>The {@code XmppSessionManager} acts as a local lookup table that maps a 
 * unique user identifier (userKey) to their corresponding WebSocket channel. 
 * This is critical for <b>final-mile delivery</b>: once a message is routed 
 * to the correct server node, this manager is used to find the pipe that 
 * pushes the data to the client's screen.</p>
 * * <p><b>Key Responsibilities:</b></p>
 * <ul>
 * <li><b>Registration:</b> Mapping a user identity to a socket after a successful 
 * XMPP bind/handshake.</li>
 * <li><b>Direct Delivery:</b> Providing the channel handles for the 
 * {@code LocalStanzaDispatcher}.</li>
 * <li><b>Graceful Shutdown:</b> Ensuring all open sockets are closed cleanly 
 * during application termination to prevent "hanging" connections.</li>
 * </ul>
 * * @author Algomeet Core Team
 */
@Component
@Slf4j
public class XmppSessionManager {
    
    /**
     * Internal thread-safe map storing active local connections.
     * Key: User Unique Identifier (e.g., userKey/JID)
     * Value: The active Netty Channel
     */
    private final Map<String, Channel> sessions = new ConcurrentHashMap<>();

    /**
     * Adds a channel to the local registry.
     * * @param userKey The unique identifier for the user.
     * @param channel The established Netty {@link Channel}.
     */
    public void register(String userKey, Channel channel) {
        sessions.put(userKey, channel);
        log.debug("Local session registered for userKey: {}. Active local count: {}", 
            userKey, sessions.size());
    }

    /**
     * Removes a user from the local registry (e.g., on disconnect).
     * * @param userKey The unique identifier of the user to remove.
     */
    public void unregister(String userKey) {
        sessions.remove(userKey);
        log.trace("Local session unregistered for userKey: {}", userKey);
    }

    /**
     * Retrieves the physical channel for a user if they are connected to this node.
     * * @param userKey The unique identifier for the user.
     * @return The {@link Channel} if found, otherwise {@code null}.
     */
    public Channel getChannel(String userKey) {
        return sessions.get(userKey);
    }

    /**
     * Spring lifecycle hook called during application shutdown.
     * Closes all physically managed channels to ensure a clean process exit.
     */
    @PreDestroy
    public void closeAll() {
        log.info("Server shutting down. Closing {} active local XMPP sessions...", sessions.size());
        sessions.values().forEach(channel -> {
            try {
                if (channel.isActive()) {
                    channel.close().syncUninterruptibly();
                }
            } catch (Exception e) {
                log.warn("Error closing channel during shutdown: {}", e.getMessage());
            }
        });
        sessions.clear();
    }
}
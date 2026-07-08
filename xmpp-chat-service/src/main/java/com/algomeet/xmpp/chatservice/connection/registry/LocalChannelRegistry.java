package com.algomeet.xmpp.chatservice.connection.registry;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Manages the physical lifecycle of active Netty Channel connections 
 * on the local server instance.
 */
@Component
@Slf4j
public class LocalChannelRegistry {
    
    /**
     * Outer Key: userKey
     * Inner Key: sessionId
     * Value: Netty Channel
     */
    private final Map<String, Map<String, Channel>> userSessions = new ConcurrentHashMap<>();

    /**
     * Combined Netty group to perform highly optimized, parallelized mass closures on shutdown.
     */
    private final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * Adds a channel to the local registry safely across multi-threaded handshakes.
     */
    public void register(String userKey, String sessionId, Channel channel) {
        // FIX: Atomic computeIfAbsent prevents simultaneous registrations from overwriting each other
        userSessions.computeIfAbsent(userKey, k -> new ConcurrentHashMap<>())
                    .put(sessionId, channel);
        
        allChannels.add(channel); // Track it for fast shutdown execution
        
        log.debug("Local session registered for userKey: {}, sessionId: {}. Active unique users: {}", 
            userKey, sessionId, userSessions.size());
    }

    /**
     * Removes an isolated individual session for a user.
     */
    public void unregister(String userKey, String sessionId) {
        userSessions.computeIfPresent(userKey, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions; // Cleans up the outer map if no sessions remain
        });
        log.trace("Local session unregistered for userKey: {}, sessionId: {}", userKey, sessionId);
    }

    /**
     * Removes all sessions completely for a user.
     */
    public void unregisterAll(String userKey) {
        userSessions.remove(userKey);
        log.trace("All local sessions unregistered for userKey: {}", userKey);
    }

    /**
     * Retrieves the specific channel for a session, fallbacking gracefully to any available local session if missing.
     */
    public List<Channel> getAllChannels(String userKey) {
        Map<String, Channel> sessions = userSessions.get(userKey);
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        return sessions.values().stream().toList();
    }

    /**
     * Spring lifecycle hook called during application shutdown.
     */
    @PreDestroy
    public void closeAll() {
        log.info("Server shutting down. Closing all active local XMPP sessions concurrently via Netty...");
        
        try {
            // FIX: Closes thousands of sockets concurrently in parallel threads via Netty internal architecture
            allChannels.close().sync();
        } catch (InterruptedException e) {
            log.warn("Shutdown channel closure interrupted", e);
            Thread.currentThread().interrupt();
        }

        userSessions.clear();
        log.info("Channel registry cleared successfully.");
    }
}
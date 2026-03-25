package com.algomeet.xmpp.chatservice.session;

import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
@RequiredArgsConstructor
public class XmppRoomManager {

    private final StringRedisTemplate redisTemplate;

    // LOCAL STATE: Map of Room JID to local Netty Channels on THIS node
    private final Map<String, Set<Channel>> localOccupants = new ConcurrentHashMap<>();

    // LOCAL STATE: Track rooms per channel for fast cleanup
    private final Map<String, Set<String>> channelSubscriptions = new ConcurrentHashMap<>();

    private static final String REDIS_ROOM_PREFIX = "xmpp:room:occupants:";

    /**
     * Joins a room: Updates local memory AND Redis global state.
     */
    public void joinRoom(String roomJid, String userBareJid, Channel channel) {
        // 1. Update Local Node Memory
        localOccupants.computeIfAbsent(roomJid, k -> new CopyOnWriteArraySet<>()).add(channel);
        channelSubscriptions.computeIfAbsent(channel.id().asLongText(), k -> new CopyOnWriteArraySet<>())
                .add(roomJid);

        // 2. Update Redis Global State (Set of JIDs in this room)
        String redisKey = REDIS_ROOM_PREFIX + roomJid;
        redisTemplate.opsForSet().add(redisKey, userBareJid);

        log.info("User {} joined room {} on local node", userBareJid, roomJid);
    }

    /**
     * Leaves a room: Cleans up local memory and Redis.
     */
    public void leaveRoom(String roomJid, String userBareJid, Channel channel) {
        // 1. Local Cleanup
        Set<Channel> occupants = localOccupants.get(roomJid);
        if (occupants != null) {
            occupants.remove(channel);
        }

        // 2. Redis Cleanup
        String redisKey = REDIS_ROOM_PREFIX + roomJid;
        redisTemplate.opsForSet().remove(redisKey, userBareJid);
    }

    /**
     * Used by the LocalStanzaDispatcher to push messages to users on THIS node.
     */
    public Set<Channel> getLocalOccupants(String roomJid) {
        return localOccupants.getOrDefault(roomJid, Collections.emptySet());
    }

    /**
     * Used to identify who is NOT on this node but is in the room globally.
     */
    public Set<String> getGlobalOccupantJids(String roomJid) {
        String redisKey = REDIS_ROOM_PREFIX + roomJid;
        return redisTemplate.opsForSet().members(redisKey);
    }

    /**
     * Total Cleanup on Disconnect.
     */
    public void removeChannel(Channel channel, String userBareJid) {
        String channelId = channel.id().asLongText();
        Set<String> joinedRooms = channelSubscriptions.remove(channelId);

        if (joinedRooms != null) {
            for (String roomJid : joinedRooms) {
                leaveRoom(roomJid, userBareJid, channel);
            }
        }
    }
}
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

    // LOCAL STATE: Map of Room userKey to local Netty Channels on THIS node
    private final Map<String, Set<Channel>> localOccupants = new ConcurrentHashMap<>();

    // LOCAL STATE: Track rooms per channel for fast cleanup
    private final Map<String, Set<String>> channelSubscriptions = new ConcurrentHashMap<>();

    private static final String REDIS_ROOM_PREFIX = "xmpp:room:occupants:";

    /**
     * Joins a room: Updates local memory AND Redis global state.
     */
    public void joinRoom(String roomuserKey, String userBareuserKey, Channel channel) {
        // 1. Update Local Node Memory
        localOccupants.computeIfAbsent(roomuserKey, k -> new CopyOnWriteArraySet<>()).add(channel);
        channelSubscriptions.computeIfAbsent(channel.id().asLongText(), k -> new CopyOnWriteArraySet<>())
                .add(roomuserKey);

        // 2. Update Redis Global State (Set of userKeys in this room)
        String redisKey = REDIS_ROOM_PREFIX + roomuserKey;
        redisTemplate.opsForSet().add(redisKey, userBareuserKey);

        log.info("User {} joined room {} on local node", userBareuserKey, roomuserKey);
    }

    /**
     * Leaves a room: Cleans up local memory and Redis.
     */
    public void leaveRoom(String roomuserKey, String userBareuserKey, Channel channel) {
        // 1. Local Cleanup
        Set<Channel> occupants = localOccupants.get(roomuserKey);
        if (occupants != null) {
            occupants.remove(channel);
        }

        // 2. Redis Cleanup
        String redisKey = REDIS_ROOM_PREFIX + roomuserKey;
        redisTemplate.opsForSet().remove(redisKey, userBareuserKey);
    }

    /**
     * Used by the LocalStanzaDispatcher to push messages to users on THIS node.
     */
    public Set<Channel> getLocalOccupants(String roomuserKey) {
        return localOccupants.getOrDefault(roomuserKey, Collections.emptySet());
    }

    /**
     * Used to identify who is NOT on this node but is in the room globally.
     */
    public Set<String> getGlobalOccupantuserKeys(String roomuserKey) {
        String redisKey = REDIS_ROOM_PREFIX + roomuserKey;
        return redisTemplate.opsForSet().members(redisKey);
    }

    /**
     * Total Cleanup on Disconnect.
     */
    public void removeChannel(Channel channel, String userKey) {
        String channelId = channel.id().asLongText();
        Set<String> joinedRooms = channelSubscriptions.remove(channelId);

        if (joinedRooms != null) {
            for (String roomUserKey : joinedRooms) {
                leaveRoom(roomUserKey, userKey, channel);
            }
        }
    }
}
package com.algomeet.xmpp.chatservice.service;

import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service responsible for managing Multi-User Chat (MUC) room metadata via a distributed cache.
 * <p>
 * This service implements a <b>Cache-Aside (Lazy Loading)</b> strategy to minimize latency 
 * during XMPP stanza routing by reducing direct calls to the {@code group-service}.
 * </p>
 * <p>
 * <b>Key Features:</b>
 * <ul>
 * <li>Distributed caching using Redis with configurable TTL.</li>
 * <li>Fail-safe logic: If Redis is unavailable, the service gracefully falls back to direct API calls.</li>
 * <li>Manual eviction support for synchronization during configuration updates.</li>
 * </ul>
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupCacheService {

    private final GroupClient groupClient;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Prefix for all group-related keys in Redis to prevent namespace collisions. */
    private static final String CACHE_KEY_PREFIX = "xmpp:group:";
    
    /** * Time-to-live for cached group metadata. 
     * Configured via {@code xmpp.cache.group-ttl} in application.yml.
     */
    @Value("${cache.group.ttl:30m}")
    private Duration cacheTtl;

    /**
     * Retrieves group metadata from the cache.
     *
     * @param groupId The unique identifier of the room/group.
     * @return {@link MucRoomDto} containing room configuration and member list.
     */
    public MucRoomDto refreshGroupCache(String groupId) {
    	// Clean up first
    	evictGroup(groupId);
    	
        return getCachedGroup(groupId);
    }
    
    /**
     * Retrieves group metadata with optional cache bypass.
     * <p>
     * <b>Execution Flow:</b>
     * <ol>
     * <li>Check Redis for existing {@code MucRoomDto} (unless {@code isForceRefreshCache} is true).</li>
     * <li>If absent (Cache Miss), fetch data from {@link GroupClient}.</li>
     * <li>Asynchronously/Sequentially populate Redis with the retrieved data for future requests.</li>
     * </ol>
     * </p>
     *
     * @param groupId              The unique identifier of the room.
     * @return The {@link MucRoomDto} retrieved from cache or the source service.
     */
    public MucRoomDto getCachedGroup(String groupId) {
        String key = CACHE_KEY_PREFIX + groupId;

        // 1. Try to get from Redis
            try {
                MucRoomDto cachedDto = (MucRoomDto) redisTemplate.opsForValue().get(key);
                if (cachedDto != null) {
                    log.debug("Cache hit for group ID: {}", groupId);
                    return cachedDto;
                }
            } catch (Exception e) {
                // Fail-safe: log the error but allow the request to proceed to the database/service
                log.error("Redis error during group lookup for ID: {}. Falling back to service call.", groupId, e);
            }

        // 2. Cache miss or forced refresh - Call Feign Client
        log.debug("Fetching group ID: {} from group-service", groupId);
        MucRoomDto roomDto = groupClient.getGroupById(groupId);

        // 3. Populate Redis for next time
        if (roomDto != null) {
            try {
                redisTemplate.opsForValue().set(key, roomDto, cacheTtl);
            } catch (Exception e) {
                log.error("Failed to populate Redis for group ID: {}. Observability may be impacted.", groupId, e);
            }
        }

        return roomDto;
    }

    /**
     * Evicts the specified group from the distributed cache.
     * <p>
     * This should be invoked via event listeners or direct API calls whenever 
     * room affiliations, roles, or configurations are updated in the primary database.
     * </p>
     *
     * @param groupId The ID of the group to evict.
     */
    public void evictGroup(String groupId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + groupId);
            log.info("Evicted group ID: {} from cache successfully.", groupId);
        } catch (Exception e) {
            log.error("Failed to evict group ID: {} from cache.", groupId, e);
        }
    }
}

package com.algomeet.xmpp.chatservice.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.dto.Group;
import com.algomeet.common.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.client.GroupClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucUserGroupsCacheService {

    private final GroupClient groupClient;
    private final RedisTemplate<String, Object> redisTemplate;  
    private final GroupCacheService groupCacheService;

    /** Prefix for all group-related keys in Redis to prevent namespace collisions. */
    private static final String CACHE_KEY_PREFIX = "xmpp:muc:user:%s:groups";
    
    /** * Time-to-live for cached group metadata. 
     * Configured via {@code muc.user-groups.cache.ttl} in application.yml.
     */
    @Value("${muc.user-groups.cache.ttl:1m}")
    private Duration cacheTtl;

    public List<String> getCachedGroupIds(String userKey) {
        String key = String.format(CACHE_KEY_PREFIX, userKey);

        // 1. Try to get from Redis (Fail-Safe read)
        try {
            String groupIdsStr = (String) redisTemplate.opsForValue().get(key);
            if (groupIdsStr != null) {
                log.debug("Cache hit for user groups: {}", userKey);
                // Handle cached empty indicator or split list cleanly
                return groupIdsStr.isEmpty() ? List.of() : List.of(groupIdsStr.split(","));
            }
        } catch (Exception e) {
            log.error("Redis error during group lookup for user: {}. Falling back to service call.", userKey, e);
        }

        // 2. Cache miss - Call Feign Client
        log.debug("Cache miss. Fetching user groups for: {} from MUC user-group-service", userKey);
        List<Group> roomDtos = groupClient.getGroupsForUserKey(userKey);
        
        // Add group all group objects to cache
        groupCacheService.addToCache(roomDtos).subscribe();

        // 3. Process results and extract IDs
        List<String> groupIdList = Optional.ofNullable(roomDtos)
                .orElse(List.of())
                .stream()
                .map(Group::getId)
                .map(String::valueOf)
                .toList();

        // 4. Flatten to comma-separated string for cache storage
        String cacheValue = String.join(",", groupIdList);

        // 5. Populate Redis for next time (Fail-Safe write)
        try {
            redisTemplate.opsForValue().set(key, cacheValue, cacheTtl);
        } catch (Exception e) {
            // Fixed the typo here changing 'groupId' to 'userKey'
            log.error("Failed to populate Redis cache for user: {}. Observability may be impacted.", userKey, e);
        }

        return groupIdList;
    }   
}

package com.algomeet.common.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.Group;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractGroupCache {
	@Autowired
    private GroupClientService groupService;
	
	@Autowired
    private RedisTemplate<String, Object> redisTemplate;

    protected static final String CACHE_KEY_PREFIX = "common:groups:";
    private static final Group EMPTY_SENTINEL = new Group();

    @Value("${common.groups.cache.ttl:30m}")
    protected Duration cacheTtl;    

    // Enforce generic parameters on the constructor signature
    public AbstractGroupCache(GroupClientService groupService, RedisTemplate<String, Object> redisTemplate) {
        this.groupService = groupService;
        this.redisTemplate = redisTemplate;
    }

    public Group refreshGroupCache(String groupId) {
        evictGroup(groupId);
        return getCachedGroup(groupId);
    }
    
    public Group getCachedGroup(String groupId) {
        String key = getCacheKey(groupId);

        try {
            Object cachedObj = redisTemplate.opsForValue().get(key);
            if (cachedObj != null) {
                if (cachedObj == EMPTY_SENTINEL) {
                    log.debug("Cache penetration match: group ID {} flagged as non-existent.", groupId);
                    return null;
                }
                log.debug("Cache hit for group ID: {}", groupId);
                return (Group) cachedObj;
            }
        } catch (Exception e) {
            log.error("Redis unreachable during lookup for ID: {}. Falling back to direct client service invocation.", groupId, e);
        }

        synchronized (this) {
            try {
                Object secondaryCheck = redisTemplate.opsForValue().get(key);
                if (secondaryCheck != null) {
                    return secondaryCheck == EMPTY_SENTINEL ? null : (Group) secondaryCheck;
                }
            } catch (Exception ignored) {}

            log.debug("Cache miss encountered. Fetching group ID: {} from downstream group-service.", groupId);
            Group roomDto = null;
            try {
                roomDto = groupService.getGroupById(groupId);
            } catch (Exception e) {
                log.error("Downstream group-service lookup failed critically for group ID: {}", groupId, e);
                throw e;
            }

            try {
                if (roomDto != null) {
                    redisTemplate.opsForValue().set(key, roomDto, cacheTtl);
                } else {
                    redisTemplate.opsForValue().set(key, EMPTY_SENTINEL, Duration.ofMinutes(5));
                }
            } catch (Exception e) {
                log.error("Failed to populate Redis for group ID: {}.", groupId, e);
            }

            return roomDto;
        }
    }

    public void evictGroup(String groupId) {
        try {
            redisTemplate.delete(getCacheKey(groupId));
            log.info("Evicted group ID: {} from cache successfully.", groupId);
        } catch (Exception e) {
            log.error("Failed to evict group ID: {} from cache.", groupId, e);
        }
    }    
    
    public List<Group> getGroups(List<String> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyList();
        }

        List<String> cleanGroupIds = groupIds.stream().filter(Objects::nonNull).distinct().toList();
        List<String> prefixedKeys = cleanGroupIds.stream().map(this::getCacheKey).toList();

        Map<String, Group> resultMap = new HashMap<>();
        List<String> cacheMissIds = new ArrayList<>();

        try {
            List<Object> cachedValues = redisTemplate.opsForValue().multiGet(prefixedKeys);
            if (cachedValues != null) {
                for (int i = 0; i < cleanGroupIds.size(); i++) {
                    String currentId = cleanGroupIds.get(i);
                    Object cachedObj = cachedValues.get(i);

                    if (cachedObj != null) {
                        if (cachedObj != EMPTY_SENTINEL) {
                            resultMap.put(currentId, (Group) cachedObj);
                        }
                    } else {
                        cacheMissIds.add(currentId);
                    }
                }
            } else {
                cacheMissIds.addAll(cleanGroupIds);
            }
        } catch (Exception e) {
            log.error("Redis bulk lookup processing error. Redirecting mass query footprint to client engine.", e);
            cacheMissIds.addAll(cleanGroupIds);
        }

        if (!cacheMissIds.isEmpty()) {
            log.debug("Bulk fetch cache misses encountered for elements: {}. Resolving components synchronously.", cacheMissIds);
            for (String missId : cacheMissIds) {
                try {
                    Group fetchedGroup = getCachedGroup(missId);
                    if (fetchedGroup != null) {
                        resultMap.put(missId, fetchedGroup);
                    }
                } catch (Exception e) {
                    log.error("Failed handling single fallback collection iteration loop for entity: {}", missId, e);
                }
            }
        }

        return cleanGroupIds.stream()
                .map(resultMap::get)
                .filter(Objects::nonNull)
                .toList();
    }   
    
    protected String getCacheKey(String groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("Group ID cannot be null");
        }
        return CACHE_KEY_PREFIX + groupId;
    }
}
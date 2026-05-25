package com.algomeet.xmpp.chatservice.service;

import com.algomeet.xmpp.chatservice.client.GroupClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucRoomService {

    private final GroupClient groupClient;
    private final GroupCacheService groupCacheService; // Inject the passive cache provider

    /**
     * Handles the business flow for clearing a member's history timeline.
     * Orchestrates the remote mutation and ensures the local cache consistency.
     */
    public Boolean clearMemberHistoryTimeline(UUID groupId, UUID userKey, Long historyCutoff) {
        // 1. Execute the remote core business mutation
        boolean isCleared = groupClient.clearMemberHistoryTimeline(groupId, userKey, historyCutoff);
        
        // 2. Manage side-effects (Cache Eviction) safely on this side of the boundary
        if (isCleared) {            
            groupCacheService.evictGroup(groupId.toString());
            log.debug("Successfully updated remote timeline cutoff parameters for user {} in group {}. Evicting cache...", userKey, groupId);
        } else {
            log.warn("Remote group service reported no state changes for user {} in group {}. Cache eviction skipped.", userKey, groupId);
        }
        
        return isCleared;
    }
}
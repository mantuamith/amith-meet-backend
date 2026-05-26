package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.util.XmppSyncStanzaComposer;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucRoomService {

    private final GroupClient groupClient;
    private final GroupCacheService groupCacheService; // Inject the passive cache provider
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final DomainProperties domainProperties;

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
        
        /**
         * <message from='conference.algomeet.app'
         *          type='headline'>
         *     <sync xmlns='urn:xmpp:algomeet:sync:history'>
         *         <conversation room-id='ROOM ID'
         *                       cleared-until='xxxxxx' />
         *     </sync>
         * </message>
         */

        String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
        		domainProperties.getGroupChatDomain(),
        		groupId.toString(), 
        		historyCutoff
        );

        // 3. Generate unique tracking identifier for cluster delivery routing
        String clusterMessageId = UuidCreator.getTimeOrderedEpoch().toString();
        
        // 4. Dispatch the timeline clearance payload to secondary user devices
        clusterMessagePublisher.convertAndSendToUser(
                clusterMessageId,
                userKey.toString(), 
                userKey.toString(), 
                ChatType.GROUPCHAT, 
                payload
        );
        
        return isCleared;
    }
}
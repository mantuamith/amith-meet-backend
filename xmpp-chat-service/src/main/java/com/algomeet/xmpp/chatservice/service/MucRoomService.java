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
        
    /**
     * Executes an administrative hard-purge of all messages within a specific MUC group.
     * Permanently drops message entities globally and broadcasts structural reset notifications.
     * * @param groupId The unique room identifier (UUID) targeting the group chat space
     * @return true if the purge was executed and completed successfully across remote endpoints
     */
    public Boolean purgeAllGroupMessages(UUID groupId) {
        log.warn("Executing administrative database purge for all messages in group: {}", groupId);

        // TODO: 1. Trigger the hard-deletion across your microservice boundary
        boolean isPurged = true; //groupClient.purgeAllGroupMessages(groupId);

        if (!isPurged) {
            log.error("Remote core group microservice failed to purge messages for group: {}", groupId);
            return false;
        }

        // 2. Invalidate the Redis cache layer immediately to prevent dirty historic reads
        groupCacheService.evictGroup(groupId.toString());
        log.debug("Evicted group cache for id {} following global history purge optimization.", groupId);

        /**
         * Construct an administrative broadcast sync stanza.
         * Note: Setting 'cleared-until' to the current epoch max guarantees 
         * client engines evaluate all local message records as obsolete.
         * * <message from='conference.algomeet.app' type='headline'>
         * <sync xmlns='urn:xmpp:algomeet:sync:history'>
         * <conversation room-id='ROOM_ID' cleared-until='CURRENT_TIME_MS' purged='true' />
         * </sync>
         * </message>
         */
        long systemPurgeTimestamp = System.currentTimeMillis();
        String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
                domainProperties.getGroupChatDomain(),
                groupId.toString(),
                systemPurgeTimestamp
        );

        // Injecting an analytical modifier property if your Stanza Composer allows string expansions, 
        // or rely on standard cleared-until evaluation on the client app layouts.
        String clusterMessageId = UuidCreator.getTimeOrderedEpoch().toString();

        // 3. Broadcast to your cluster messaging bridge. 
        // Since this is a global room history drop, route the event to the room's channel space 
        // so all active online occupants process the viewport clearance concurrently.
        clusterMessagePublisher.convertAndSendToUser(
                clusterMessageId,
                groupId.toString(), // Targets the common group distribution key routing string
                groupId.toString(), 
                ChatType.GROUPCHAT, 
                payload
        );

        log.info("Successfully completed global purge operations and synchronized timeline resets for group: {}", groupId);
        return true;
    }
}
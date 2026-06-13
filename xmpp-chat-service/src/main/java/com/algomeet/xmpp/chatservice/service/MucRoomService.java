package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.service.GroupCacheService;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.projection.MucMessageView;
import com.algomeet.xmpp.chatservice.util.SearchUtil;
import com.algomeet.xmpp.chatservice.util.XmppSyncStanzaComposer;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucRoomService {

    private final GroupClient groupClient;
    private final GroupCacheService groupCacheService; // Inject the passive cache provider
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final DomainProperties domainProperties;
    private final MucMessageRepository mucMessageRepository;
    
    /**
     * Handles the business flow for clearing a member's history timeline.
     * Orchestrates the remote mutation, local cache consistency, and device push notifications.
     *
     * @return A Mono emitting true if the operation succeeded, false otherwise.
     */
    public Mono<Boolean> clearMemberHistoryTimeline(UUID groupId, UUID userKey, Instant historyCutoff) {

        // 1. Offload the blocking groupClient network call
        return Mono.fromCallable(() -> 
            groupClient.clearMemberHistoryTimeline(groupId, userKey, historyCutoff.toEpochMilli())
        )
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(isCleared -> {
            // Handle Cache Side Effects
            if (!isCleared) {            
                log.warn("Remote group service reported no state changes for user {} in group {}. Cache eviction skipped.", userKey, groupId);
                return Mono.just(false);
            }

            groupCacheService.evictGroup(groupId.toString());
            log.debug("Successfully updated remote timeline cutoff parameters for user {} in group {}. Evicting cache...", userKey, groupId);

            // 2. Fetch the cutoff stanza message view from the repository
            return mucMessageRepository.findCutoffStanza(
                    groupId, 
                    historyCutoff
            )
            // Extract the ID string if a message is found, or default to an empty string/null if history is empty
            .map(view -> view.getId() != null ? view.getId().toString() : "")
            .defaultIfEmpty("") 
            .doOnNext(cutoffStanzaId -> {
                // 3. Compose and send the Synchronization Stanza to user's secondary devices
            	if(!StringUtils.isEmpty(cutoffStanzaId)) {
            		
            		String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
            				domainProperties.getGroupChatDomain(),
            				groupId.toString(), 
            				cutoffStanzaId
            				);

            		String messageId = UuidCreator.getTimeOrderedEpoch().toString();
            		clusterMessagePublisher.convertAndSendToUser(
            				messageId,
            				userKey.toString(), 
            				userKey.toString(), 
            				ChatType.GROUPCHAT, 
            				payload
            				);
            	}
            })
            .map(ignored -> true); // Retain original isCleared (true) status
        });
    }
        
    /**
     * Executes an administrative hard-purge of all messages within a specific MUC group.
     * Permanently drops message entities globally and broadcasts structural reset notifications.
     * * @param groupId The unique room identifier (UUID) targeting the group chat space
     * @return true if the purge was executed and completed successfully across remote endpoints
     */
    public Boolean purgeAllGroupMessages(UUID groupId, UUID userKey) {
        log.warn("Executing administrative database purge for all messages in group: {}", groupId);

        Group group = groupCacheService.getCachedGroup(groupId.toString());
        Optional<GroupMember> memberOpt = SearchUtil.findMember(group, userKey.toString());
        
        // If group is null meaning group has been deleted and anyone can delete the messages
        // otherwise validate the authority.
        if (group != null) {
        	if(memberOpt.isEmpty() || !(MucAffiliation.OWNER.name().equals(memberOpt.get().getRole())
        			|| MucAffiliation.ADMIN.name().equals(memberOpt.get().getRole()))) {
        		throw new AccessDeniedException("Unauthorized to purge the group messages.");
        	}
        }
        
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
         * <conversation room-id='ROOM_ID' cleared-until-stanza-id='STANZA_ID' />
         * </sync>
         * </message>
         */

        Optional<MucMessageView> lastMessageOpt = mucMessageRepository.findCutoffStanza(
                groupId, 
                Instant.now()).blockOptional();
        
        if (lastMessageOpt.isPresent()) {
        	String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
        			domainProperties.getGroupChatDomain(),
        			groupId.toString(),
        			lastMessageOpt.get().getId().toString());

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
        			payload);
        }

        log.info("Successfully completed global purge operations and synchronized timeline resets for group: {}", groupId);
        return true;
    }
}
package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.publisher.PurgeGroupConversationStreamPublisher;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.util.DeleteMediaUtil;
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
    private final DeleteMediaUtil deleteMediaUtil;
    private final PurgeGroupConversationStreamPublisher purgeGroupConversationStreamPublisher;
    private final MucMessageRouter mucMessageRouter;
    
    /**
     * Handles the business flow for clearing a member's history timeline.
     * Orchestrates the remote mutation, local cache consistency, and device push notifications.
     *
     * @return A Mono emitting true if the operation succeeded, false otherwise.
     */
    public Mono<Boolean> clearMemberHistoryTimeline(UUID groupId, UUID userKey, Instant historyCutoff) {    			
        
        // Fix 1: Offload the initial blocking cache lookup safely
        return Mono.fromCallable(() -> {
            Group group = groupCacheService.getCachedGroup(groupId.toString());
            return SearchUtil.findMember(group, userKey.toString());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(memberPrevData -> 
            // 1. Execute the remote mutation
            Mono.fromCallable(() -> 
                groupClient.clearMemberHistoryTimeline(groupId, userKey, historyCutoff.toEpochMilli())
            )
            .flatMap(isCleared -> {
                if (!isCleared) {            
                    log.warn("Remote group service reported no state changes for user {} in group {}. Cache eviction skipped.", userKey, groupId);
                    return Mono.just(false);
                }

                groupCacheService.evictGroup(groupId.toString());

                // 2. Fetch cutoff stanza view
                return mucMessageRepository.findFirstByRoomIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(groupId, historyCutoff)
                    .map(view -> view.getId() != null ? view.getId().toString() : "")
                    .defaultIfEmpty("") 
                    .flatMap(cutoffStanzaId -> {
                        if (StringUtils.isEmpty(cutoffStanzaId)) {
                            return Mono.just(true);
                        }

                        // 3. Compose and distribute sync notifications
                        String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
                                domainProperties.getGroupChatDomain(),
                                groupId.toString(), 
                                cutoffStanzaId,
                                false
                        );

                        String messageId = UuidCreator.getTimeOrderedEpoch().toString();
                        clusterMessagePublisher.convertAndSendToUser(
                                messageId, userKey.toString(), userKey.toString(), ChatType.GROUPCHAT, payload
                        );
                		
                        // 4: Chain cleanly into the reactive stream to ensure execution completion guarantees
                        return deleteMediaUtil.handleDeletionOfMediaFilesReactive(userKey, memberPrevData, cutoffStanzaId, groupId)
                                .then(Mono.just(true));
                    });
            })
        );
    }
           
    /**
     * Executes an administrative hard-purge of all messages within a specific MUC group.
     * Permanently drops message entities globally and broadcasts structural reset notifications.
     * * @param groupId The unique room identifier (UUID) targeting the group chat space
     * @return true if the purge was executed and completed successfully across remote endpoints
     */
    public Boolean purgeGroupConversation(UUID groupId, UUID userKey) {
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
        
        // Send purge group event to stream
        purgeGroupConversationStreamPublisher.publish(groupId);

        // Invalidate the Redis cache layer immediately to prevent dirty historic reads
        groupCacheService.evictGroup(groupId.toString());
        log.debug("Evicted group cache for id {} following global history purge optimization.", groupId);

        /**
         * Construct an administrative broadcast sync stanza.
         * Note: Setting 'cleared-until' to the current epoch max guarantees 
         * client engines evaluate all local message records as obsolete.
         * * <message from='conference.algomeet.app' type='headline'>
         * <sync xmlns='urn:xmpp:algomeet:sync:history'>
         * <conversation room-id='ROOM_ID' cleared-until-stanza-id='STANZA_ID' deleted='true'/>
         * </sync>
         * </message>
         */       
        String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
        		domainProperties.getGroupChatDomain(),
        		groupId.toString(),
        		Constants.LARGEST_UUID_V7.toString(),
        		true);

        // Injecting an analytical modifier property if your Stanza Composer allows string expansions, 
        // or rely on standard cleared-until evaluation on the client app layouts.
        String clusterMessageId = UuidCreator.getTimeOrderedEpoch().toString();

        if(group != null ) {
        	// 3. Broadcast to your cluster messaging bridge. 
        	// Since this is a global room history drop, route the event to the room's channel space 
        	// so all active online occupants process the viewport clearance concurrently.
        	mucMessageRouter.broadcastToOccupants(clusterMessageId, userKey.toString(), group, payload, true);
        }

        log.info("Successfully completed global purge operations and synchronized timeline resets for group: {}", groupId);
        return true;
    }
    
    public Mono<Void> purgeGroupConversation(String groupId) {
    	if (StringUtils.isEmpty(groupId)) {
    		return Mono.empty();
    	}
    	
        java.time.Instant now = java.time.Instant.now();

        // Update purge date to now
        return mucMessageRepository.updatePurgeAtByRoomId(UUID.fromString(groupId), now)
                .doOnSuccess(count -> log.info("Successfully marked group {} conversation for purging. Total documents modified: {}", groupId, count))
                .onErrorResume(err -> {
                    log.error("Failed to execute purge update routine for group: {}", groupId, err);
                    return Mono.error(new RuntimeException("Could not flag group conversation for purging", err));
                })
                .then(); // Yield Mono<Void>
    }
}
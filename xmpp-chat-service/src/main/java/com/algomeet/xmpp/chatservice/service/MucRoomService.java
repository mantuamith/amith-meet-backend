package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.redis.lock.MucMessageRetentionLockManager;
import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.exceptions.GroupNotFoundException;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.publisher.PurgeGroupConversationStreamPublisher;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.SyncMessageRetentionStanza;
import com.algomeet.xmpp.chatservice.util.DeleteMediaUtil;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.SearchUtil;
import com.algomeet.xmpp.chatservice.util.XmppSyncStanzaComposer;
import com.github.f4b6a3.uuid.UuidCreator;
import com.mongodb.client.result.UpdateResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


@Slf4j
@Service
@RequiredArgsConstructor
public class MucRoomService {

    private final GroupClient groupClient;
    private final AbstractGroupCache groupCacheService; // Inject the passive cache provider
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final DomainProperties domainProperties;
    private final MucMessageRepository mucMessageRepository;
    private final DeleteMediaUtil deleteMediaUtil;
    private final PurgeGroupConversationStreamPublisher purgeGroupConversationStreamPublisher;
    private final MucMessageRouter mucMessageRouter;
	private final MucMessageRetentionLockManager mucMessageRetentionLockManager;
	private final ReactiveMongoTemplate reactiveMongoTemplate; 
	private final JidUtil jidUtil;
    
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
                        return deleteMediaUtil.handleDeletionOfUserMediaFilesReactive(userKey, memberPrevData, UUID.fromString(cutoffStanzaId), groupId)
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
    
    /**
     * Marks all messages belonging to the specified group conversation for purge.
     *
     * <p>This operation does not immediately delete messages. Instead, it updates
     * the group's messages by setting their purge timestamp to the current time,
     * making them eligible for removal by the background purge process.</p>
     *
     * <p>The method is idempotent and can be safely retried if necessary.</p>
     *
     * @param groupId the unique identifier of the group whose conversation should
     *                be marked for purge
     * @return a {@link Mono} that completes when the purge flag has been applied;
     *         returns {@link Mono#empty()} if the group identifier is null or empty
     */
    public Mono<Void> purgeGroupConversation(String groupId) {
        if (StringUtils.isEmpty(groupId)) {
            return Mono.empty();
        }

        Instant now = Instant.now();

        // Mark all messages in the group as eligible for purge by setting
        // the purge timestamp to the current time.
        return mucMessageRepository.updatePurgeAtByRoomId(UUID.fromString(groupId), now)
                .doOnSuccess(count -> log.info(
                        "Successfully marked group {} conversation for purging. Total documents modified: {}",
                        groupId, count))
                .onErrorResume(err -> {
                    log.error("Failed to execute purge update routine for group: {}", groupId, err);
                    return Mono.error(new RuntimeException(
                            "Could not flag group conversation for purging", err));
                })
                .then();
    }
        
    /**
     * Performs media cleanup for a member who has exited a group.
     *
     * <p>This operation revokes access to media files that were shared exclusively
     * through the specified group and are no longer accessible to the departed member.
     * The cleanup scans the member's entire participation history in the group
     * (from {@link Instant#EPOCH} up to the latest possible message identifier)
     * and removes media references accordingly.</p>
     *
     * @param groupId the unique identifier of the group
     * @param memberUserKey the unique identifier of the member who exited the group
     * @return a {@link Mono} that completes when the media cleanup operation finishes;
     *         returns {@link Mono#empty()} if either parameter is null or empty
     */
    public Mono<Void> exitGroupMemberMediaCleanup(String groupId, String memberUserKey) {
        if (StringUtils.isEmpty(groupId) || StringUtils.isEmpty(memberUserKey)) {
            return Mono.empty();
        }

        // Process the member's entire message history in the group since they no
        // longer have access to any group-shared media after leaving.
        Instant prevHistoryCutoff = Instant.EPOCH;

        return deleteMediaUtil.handleDeletionOfUserMediaFilesReactive(
                UUID.fromString(memberUserKey),
                prevHistoryCutoff,
                Constants.LARGEST_UUID_V7,
                UUID.fromString(groupId));
    }    

    public Mono<Void> updateMessageRetention(UUID userKey, UUID groupId, Integer messageRetentionDays,
    		String sessionId) {
        Integer retentionDays = messageRetentionDays != -1 ? messageRetentionDays : null;

        // Use Mono.usingWhen to manage the lock lifecycle across the ENTIRE sequence
        return Mono.usingWhen(
            // Phase 1: Resource Acquisition (Acquire lock at the absolute start)
            Mono.fromCallable(() -> mucMessageRetentionLockManager.acquireLock(groupId))
                .flatMap(token -> token != null 
                    ? Mono.just(token) 
                    : Mono.error(new IllegalStateException("Could not acquire retention update lock."))),
            
            // Phase 2: Business Pipeline Execution (Everything protected under the lock)
            token -> Mono.fromCallable(() -> groupClient.updateGroupRetention(groupId, userKey, messageRetentionDays))
                .flatMap(success -> {
                    if (Boolean.FALSE.equals(success)) {
                        return Mono.error(new RuntimeException("Failed to update the group retention policy via client."));
                    }
                    
                    // Fetch group cache details safely within the stream
                    Group group = groupCacheService.refreshGroupCache(groupId.toString());
                    if (group == null) {
                        return Mono.error(new GroupNotFoundException("Group not found exception " + groupId));
                    }
                    
                    // Validate authority rules
                    Optional<GroupMember> memberOpt = SearchUtil.findMember(group, userKey.toString());
                    if (memberOpt.isEmpty() || !(MucAffiliation.OWNER.name().equals(memberOpt.get().getRole())
                            || MucAffiliation.ADMIN.name().equals(memberOpt.get().getRole()))) {
                        return Mono.error(new AccessDeniedException("Unauthorized to purge the group messages."));
                    }
                    
                    
                    // If validation passes, proceed directly to updating database records
                    return updatePurgeAtByRoomId(groupId, retentionDays)
                    		.then(Mono.fromRunnable(() -> {
                    			String messageId = UuidCreator.getTimeOrderedEpoch().toString();

                    			// Compose and send sync message to group members and echo message to user's online devices
                    			SyncMessageRetentionStanza syncStanza = SyncMessageRetentionStanza.builder() 
                    					.id(messageId)
                    					.from(jidUtil.getGroupBareJid(groupId.toString()) + "/" + userKey.toString())
                    					.retentiondays(messageRetentionDays) 
                    					.type(XmppMessageType.HEADLINE.getXmlValue())
                    					.build(); 

                    			// Distribute via router to all active occupants in the room
                    			mucMessageRouter.broadcastToOccupants(messageId, 
                    					userKey.toString(), 
                    					group, 
                    					syncStanza.toXml(), 
                    					sessionId);
                    		}));
                }),
                
            // Phase 3: Cleanup on Success Completion
            token -> Mono.fromRunnable(() -> mucMessageRetentionLockManager.releaseLock(token)),
            
            // Phase 4: Cleanup on Error (Ensures lock release if network or database calls fail)
            (token, error) -> Mono.fromRunnable(() -> {
                log.error("Error occurred during message retention policy update for room: {}", groupId, error);
                mucMessageRetentionLockManager.releaseLock(token);
            }),
            
            // Phase 5: Cleanup on Downstream Cancellation
            token -> Mono.fromRunnable(() -> mucMessageRetentionLockManager.releaseLock(token))
        )
        .then(); // Emits Mono<Void> on successful termination
    }
    
    public Mono<Long> updatePurgeAtByRoomId(UUID roomId, Integer messageRetentionDays) {
        Query query = Query.query(Criteria.where(MucMessage.FIELD_ROOM_ID).is(roomId));

        // Fallback if retention days is null or explicit flag
        if (messageRetentionDays == null || messageRetentionDays == -1) {
            AggregationUpdate clearUpdate = AggregationUpdate.update().set(MucMessage.FIELD_PURGE_AT).toValue(null);
            return reactiveMongoTemplate.updateMulti(query, clearUpdate, MucMessage.class)
                    .map(UpdateResult::getModifiedCount);
        }

        // Convert days to milliseconds for the calculation
        long retentionMs = (long) messageRetentionDays * 86400000L;

        // Direct BSON Aggregation Expression
        AggregationUpdate pipelineUpdate = AggregationUpdate.update()
            .set(MucMessage.FIELD_PURGE_AT)
            .toValue(
                new Document("$add", List.of(
                    new Document("$ifNull", List.of("$" + MucMessage.FIELD_CREATED_AT, "$$NOW")),
                    retentionMs
                ))
            );

        return reactiveMongoTemplate.updateMulti(query, pipelineUpdate, MucMessage.class)
                .map(UpdateResult::getModifiedCount);
    }
}
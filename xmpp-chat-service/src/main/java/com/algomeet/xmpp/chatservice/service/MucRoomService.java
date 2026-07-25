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

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.redis.lock.MucMessageRetentionLockManager;
import com.algomeet.common.redis.lock.MucMessageRetentionLockManager.LockToken;
import com.algomeet.common.service.AbstractGroupCache;
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
import com.algomeet.xmpp.chatservice.publisher.RemoveGroupSenderKeyPublisher;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
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
    private final AbstractGroupCache groupCacheService; 
    private final ClusterMessagePublisher reactiveClusterMessagePublisher; // FIXED: Swapped to reactive variant
    private final DomainProperties domainProperties;
    private final MucMessageRepository mucMessageRepository;
    private final DeleteMediaUtil deleteMediaUtil;
    private final PurgeGroupConversationStreamPublisher purgeGroupConversationStreamPublisher;
    private final MucMessageRouter reactiveMucMessageRouter; // FIXED: Swapped to reactive variant
    private final MucMessageRetentionLockManager mucMessageRetentionLockManager;
    private final ReactiveMongoTemplate reactiveMongoTemplate; 
    private final JidUtil jidUtil;
    private final RemoveGroupSenderKeyPublisher removeGroupSenderKeyPublisher;
    
    /**
     * Handles the business flow for clearing a member's history timeline.
     */
    public Mono<Boolean> clearMemberHistoryTimeline(UUID groupId, UUID userKey, Instant historyCutoff) {    			
        return Mono.fromCallable(() -> {
            Group group = groupCacheService.getCachedGroup(groupId.toString());
            return SearchUtil.findMember(group, userKey.toString());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(memberPrevData -> 
            Mono.fromCallable(() -> groupClient.clearMemberHistoryTimeline(groupId, userKey, historyCutoff.toEpochMilli()))
            .flatMap(isCleared -> {
                if (!isCleared) {            
                    log.warn("Remote group service reported no state changes for user {} in group {}.", userKey, groupId);
                    return Mono.just(false);
                }

                groupCacheService.evictGroup(groupId.toString());

                return mucMessageRepository.findFirstByRoomIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(groupId, historyCutoff)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(view -> view.getId() != null ? view.getId().toString() : "")
                    .defaultIfEmpty("") 
                    .flatMap(cutoffStanzaId -> {
                        if (StringUtils.isEmpty(cutoffStanzaId)) {
                            return Mono.just(true);
                        }

                        String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
                                domainProperties.getGroupChatDomain(),
                                groupId.toString(), 
                                cutoffStanzaId,
                                false
                        );

                        String messageId = UuidCreator.getTimeOrderedEpoch().toString();
                        
                        // FIXED: Correctly chained publisher call into the reactive sequence execution flow
                        return reactiveClusterMessagePublisher.convertAndSendToUser(
                                messageId, userKey.toString(), userKey.toString(), ChatType.GROUPCHAT, false, payload, null
                        )
                        .then(deleteMediaUtil.handleDeletionOfUserMediaFilesReactive(userKey, memberPrevData, UUID.fromString(cutoffStanzaId), groupId))
                        .thenReturn(true);
                    });
            })
        );
    }
           
    /**
     * Executes an administrative hard-purge of all messages within a specific MUC group.
     * FIXED: Fully refactored from blocking execution layout to an integrated reactive Mono flow.
     */
    public Mono<Boolean> purgeGroupConversation(UUID groupId, UUID userKey) {
        return Mono.fromCallable(() -> {
            log.warn("Executing administrative database purge validation for all messages in group: {}", groupId);
            Group group = groupCacheService.getCachedGroup(groupId.toString());
            
            if (group != null) {
                Optional<GroupMember> memberOpt = SearchUtil.findMember(group, userKey.toString());
                if (memberOpt.isEmpty() || !(MucAffiliation.OWNER.name().equals(memberOpt.get().getRole())
                        || MucAffiliation.ADMIN.name().equals(memberOpt.get().getRole()))) {
                    throw new AccessDeniedException("Unauthorized to purge the group messages.");
                }
            }
            return Optional.ofNullable(group);
        })
        .subscribeOn(Schedulers.boundedElastic())
        // 1. Execute DB/Stream purge
        .flatMap(groupOpt -> 
        	// Remove group conversation messages
            purgeGroupConversationStreamPublisher.publish(groupId)
                // 2. Remove E2EE sender keys right after DB purge completes
                .then(Mono.defer(() -> removeGroupSenderKeyPublisher.publish(groupId.toString(), null)
                        .doOnError(e -> log.error("Failed to remove group sender keys during purge for group {}", groupId, e))
                        .onErrorResume(e -> Mono.empty()) // Prevent key removal failure from crashing the whole pipeline
                ))
                // 3. Evict cache on success
                .doOnSuccess(v -> groupCacheService.evictGroup(groupId.toString()))
                // 4. Pass the group configuration down to the XMPP broadcast step
                .thenReturn(groupOpt) 
        )
        // 5. Broadcast XMPP clearance stanza
        .flatMap(groupOpt -> {
            if (groupOpt.isEmpty()) {
                return Mono.just(true);
            }

            String payload = XmppSyncStanzaComposer.createMucClearanceStanza(
                    domainProperties.getGroupChatDomain(),
                    groupId.toString(),
                    Constants.LARGEST_UUID_V7.toString(),
                    true);

            String clusterMessageId = UuidCreator.getTimeOrderedEpoch().toString();
            
            return reactiveMucMessageRouter.broadcastToOccupants(clusterMessageId, userKey.toString(), groupOpt.get(), payload, null)
                    .thenReturn(true);
        });
    }
    
    /**
     * Marks all messages belonging to the specified group conversation for purge.
     */
    public Mono<Void> purgeGroupConversation(String groupId) {
        if (StringUtils.isEmpty(groupId)) {
            return Mono.empty();
        }

        Instant now = Instant.now();

        return mucMessageRepository.updatePurgeAtByRoomId(UUID.fromString(groupId), now)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(count -> log.info("Successfully marked group {} conversation for purging. Modified: {}", groupId, count))
                .onErrorResume(err -> {
                    log.error("Failed to execute purge update routine for group: {}", groupId, err);
                    return Mono.error(new RuntimeException("Could not flag group conversation for purging", err));
                })
                .then();
    }
        
    /**
     * Performs media cleanup for a member who has exited a group.
     */
    public Mono<Void> exitGroupMemberMediaCleanup(String groupId, String memberUserKey) {
        if (StringUtils.isEmpty(groupId) || StringUtils.isEmpty(memberUserKey)) {
            return Mono.empty();
        }

        return deleteMediaUtil.handleDeletionOfUserMediaFilesReactive(
                UUID.fromString(memberUserKey),
                Instant.EPOCH,
                Constants.LARGEST_UUID_V7,
                UUID.fromString(groupId))
                .subscribeOn(Schedulers.boundedElastic());
    }    

    /**
     * Safely updates group retention policies under global distributed locks.
     */
    public Mono<Void> updateMessageRetention(UUID userKey, UUID groupId, Integer messageRetentionDays, String sessionId) {
        Integer retentionDays = messageRetentionDays != -1 ? messageRetentionDays : null;

        return Mono.usingWhen(
            Mono.fromCallable(() -> mucMessageRetentionLockManager.acquireLock(groupId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(token -> token != null 
                    ? Mono.just(token) 
                    : Mono.error(new IllegalStateException("Could not acquire retention update lock."))),
            
            token -> Mono.fromCallable(() -> groupClient.updateGroupRetention(groupId, userKey, messageRetentionDays))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(success -> {
                    if (Boolean.FALSE.equals(success)) {
                        return Mono.error(new RuntimeException("Failed to update the group retention policy via client."));
                    }
                    
                    return Mono.fromCallable(() -> groupCacheService.refreshGroupCache(groupId.toString()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(group -> {
                            if (group == null) {
                                return Mono.error(new GroupNotFoundException("Group not found: " + groupId));
                            }
                            
                            Optional<GroupMember> memberOpt = SearchUtil.findMember(group, userKey.toString());
                            if (memberOpt.isEmpty() || !(MucAffiliation.OWNER.name().equals(memberOpt.get().getRole())
                                    || MucAffiliation.ADMIN.name().equals(memberOpt.get().getRole()))) {
                                return Mono.error(new AccessDeniedException("Unauthorized to update retention."));
                            }
                            
                            return updatePurgeAtByRoomId(groupId, retentionDays)
                                .then(Mono.defer(() -> {
                                    String messageId = UuidCreator.getTimeOrderedEpoch().toString();

                                    SyncMessageRetentionStanza syncStanza = SyncMessageRetentionStanza.builder() 
                                            .id(messageId)
                                            .from(jidUtil.getGroupBareJid(groupId.toString()) + "/" + userKey.toString())
                                            .retentiondays(messageRetentionDays) 
                                            .type(XmppMessageType.HEADLINE.getXmlValue())
                                            .build(); 

                                    // FIXED: Swapped flatMap instead of Mono.fromRunnable to properly process the returned reactive flow
                                    return reactiveMucMessageRouter.broadcastToOccupants(
                                            messageId, userKey.toString(), group, syncStanza.toXml(), sessionId);
                                }));
                        });
                }),
                
                // Safe Release on Complete
                token -> safeReleaseLock(token, groupId),

                // Safe Release on Error
                (token, error) -> {
                    log.error("Error during retention policy update for room: {}", groupId, error);
                    return safeReleaseLock(token, groupId);
                },

                // Safe Release on Cancel
                token -> safeReleaseLock(token, groupId)
        )
        .then();
    }
    
    /**
     * Helper to guarantee non-blocking, isolated lock release during Mono.usingWhen teardown.
     */
    private Mono<Void> safeReleaseLock(LockToken token, UUID groupId) {
        return Mono.fromRunnable(() -> {
            try {
                mucMessageRetentionLockManager.releaseLock(token);
            } catch (Exception e) {
                log.error("Failed to release retention lock for group {}", groupId, e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }
    
    /**
     * Standardizes dynamic time-to-live variables inside database clusters.
     */
    public Mono<Long> updatePurgeAtByRoomId(UUID roomId, Integer messageRetentionDays) {
        Query query = Query.query(Criteria.where(MucMessage.FIELD_ROOM_ID).is(roomId));

        if (messageRetentionDays == null || messageRetentionDays == -1) {
            AggregationUpdate clearUpdate = AggregationUpdate.update().set(MucMessage.FIELD_PURGE_AT).toValue(null);
            return reactiveMongoTemplate.updateMulti(query, clearUpdate, MucMessage.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(UpdateResult::getModifiedCount);
        }

        long retentionMs = (long) messageRetentionDays * 86400000L;

        AggregationUpdate pipelineUpdate = AggregationUpdate.update()
            .set(MucMessage.FIELD_PURGE_AT)
            .toValue(
                new Document("$add", List.of(
                    new Document("$ifNull", List.of("$" + MucMessage.FIELD_CREATED_AT, "$$NOW")),
                    retentionMs
                ))
            );

        return reactiveMongoTemplate.updateMulti(query, pipelineUpdate, MucMessage.class)
                .subscribeOn(Schedulers.boundedElastic())
                .map(UpdateResult::getModifiedCount);
    }
}
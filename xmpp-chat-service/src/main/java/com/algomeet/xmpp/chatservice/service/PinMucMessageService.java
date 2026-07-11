package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.PinMucMessage;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.ViewManageEnum;
import com.algomeet.xmpp.chatservice.exceptions.PinMessageNotFoundException;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.PinMucMessageRepository;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.stanza.PinStanza;
import com.algomeet.xmpp.chatservice.stanza.ViewManageSyncStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class PinMucMessageService {
    private final PinMucMessageRepository pinMucMessageRepository;
    private final JidUtil jidUtil;
    private final DomainProperties domainProperties;
    private final MucMessageRouter reactiveMucMessageRouter;
    private final AbstractGroupCache groupCacheService;
    private final ClusterMessagePublisher reactiveClusterMessagePublisher;

    // Dedicated pool to cleanly offload blocking repository or cache actions away from Netty
    private static final Scheduler MUC_WORKER_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-pin-muc-message-workers");

    /**
     * Pins a new message inside a specific MUC room context.
     */
    public Mono<PinMucMessage> pinMessage(UUID userKey, UUID roomId, String sessionId, PinMucMessage pinMucMessage) {
        return pinMucMessageRepository.save(pinMucMessage)
                .subscribeOn(MUC_WORKER_SCHEDULER)
                .flatMap(saved -> {
                    log.debug("Successfully pinned MUC message {} in group {}", 
                            saved.getId().getMessageId(), saved.getId().getGroupId());
                    
                    // FIXED: Corrected targetId resolution logic to fetch the explicit message ID string
                    String msgIdStr = saved.getId().getMessageId().toString();
                    
                    Mono<Void> broadcastMono = saved.isPinnedForEveryone() 
                            ? composeAndSendPinForEveryone(msgIdStr, roomId.toString(), userKey.toString(), sessionId, ViewManageEnum.PIN)                            
                            : composeAndSendPinSync(msgIdStr, roomId.toString(), userKey.toString(), sessionId, ViewManageEnum.PIN);
                    
                    return broadcastMono.thenReturn(saved);
                });
    }

    /**
     * Unpins a message from a MUC room.
     */
    public Mono<Void> unpinMessage(UUID userKey, UUID groupId, UUID messageId, String sessionId) {
        return pinMucMessageRepository.deleteById_GroupIdAndId_MessageIdAndId_PinnedByAndPinnedForEveryoneIsFalse(groupId, messageId, userKey)
                .subscribeOn(MUC_WORKER_SCHEDULER)
                .flatMap(personalDeletedCount -> {
                    if (personalDeletedCount > 0) {
                        log.debug("Successfully unpinned personal message {} from MUC room {}", messageId, groupId);
                        return composeAndSendPinSync(messageId.toString(), groupId.toString(), userKey.toString(), sessionId, ViewManageEnum.UNPIN);
                    }
                    
                    return pinMucMessageRepository
                            .deleteById_GroupIdAndId_MessageIdAndPinnedForEveryoneIsTrue(groupId, messageId)
                            .flatMap(globalDeletedCount -> {
                                if (globalDeletedCount == 0) {
                                    return Mono.error(new PinMessageNotFoundException("Pinned MUC message not found."));
                                }
                                log.debug("Successfully unpinned global message {} from MUC room {}", messageId, groupId);
                                return composeAndSendPinForEveryone(messageId.toString(), groupId.toString(), userKey.toString(), sessionId, ViewManageEnum.UNPIN);
                            });
                })
                .doOnError(err -> log.error("Failed to remove MUC pin record for message {}", messageId, err))
                .then();
    }

    /**
     * Finds pinned messages matching your exact compound index structure inside a MUC space.
     */
    public Flux<PinMucMessage> findPinnedMessages(UUID groupId, UUID pinnedBy) {    	
        return pinMucMessageRepository.findPinnedMessages(groupId, pinnedBy)
                .subscribeOn(MUC_WORKER_SCHEDULER)
                .doOnError(err -> log.error("Error matching indexed pin search framework for user {} in MUC room {}", 
                        pinnedBy, groupId, err));
    }
    
    /**
	 * Generates a sync stanza to push the updated pin state to the MUC room context.
	 */
	private Mono<Void> composeAndSendPinSync(String targetId, String roomId, String userKey, String sessionId, ViewManageEnum viewManageEnum) {
        // FIXED: Wrap the blocking groupCacheService call into a deferred callable pipeline 
        return Mono.fromCallable(() -> groupCacheService.getCachedGroup(roomId))
                .subscribeOn(MUC_WORKER_SCHEDULER)
                .flatMap(group -> {
                    String id = UuidCreator.getTimeOrderedEpoch().toString();
                    ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
                            .id(id)
                            .targetId(targetId)
                            .room(roomId)				
                            .from(jidUtil.getGroupBareJid(roomId) + "/" + userKey) 
                            .action(viewManageEnum.getValue())
                            .build();

                    String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();
                    String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, domainProperties.getDomain());
                    // Sync user's other devices by sending this message to itself
                    return reactiveClusterMessagePublisher.convertAndSendToUser(
                            id, userKey, userKey, ChatType.CHAT, false, false, xml, sessionId);
                });
	}
	
	/**
	 * Generates a sync stanza to push the updated pin state out to other active multi-resource client sessions and group members.
	 */
	private Mono<Void> composeAndSendPinForEveryone(String targetId, String roomId, String userKey, String sessionId, ViewManageEnum viewManageEnum) {
        // FIXED: Wrap the blocking groupCacheService call into a deferred callable pipeline
        return Mono.fromCallable(() -> groupCacheService.getCachedGroup(roomId))
                .subscribeOn(MUC_WORKER_SCHEDULER)
                .flatMap(group -> {
                    String id = UuidCreator.getTimeOrderedEpoch().toString();
                    PinStanza pinStanza = PinStanza.builder()
                            .id(id)
                            .targetId(targetId)
                            .from(jidUtil.getGroupBareJid(roomId) + "/" + userKey)
                            .action(viewManageEnum.getValue())
                            .build();

                    String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
                    String xml = XmppStanzaUtil.insertStanzaId(pinStanza.toXml(), stanzaId, domainProperties.getDomain());
                    
                    // Sent to all group members
                    return reactiveMucMessageRouter.broadcastToOccupants(id, userKey, group, xml, sessionId);
                });
	}
}
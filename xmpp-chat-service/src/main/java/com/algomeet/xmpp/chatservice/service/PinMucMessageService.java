package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.dto.Group;
import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.xmpp.chatservice.document.PinMucMessage;
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
    private final MucMessageRouter mucMessageRouter;
    private final AbstractGroupCache groupCacheService;

    // Dedicated thread pool to offload blocking/heavy database processing off WebFlux Netty threads
    private static final Scheduler MUC_DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-muc-pin-workers");

    /**
     * Pins a new message inside a specific MUC room context.
     */
    public Mono<PinMucMessage> pinMessage(UUID userKey, UUID roomId, String sessionId, PinMucMessage pinMucMessage) {
        return pinMucMessageRepository.save(pinMucMessage)
                .flatMap(saved -> {
                    log.debug("Successfully pinned MUC message {} in group {}", 
                            saved.getId().getMessageId(), saved.getId().getGroupId());
                    
                    // Wrap the blocking void methods into a reactive Mono deferral
                    return Mono.<Void>fromRunnable(() -> {
                        if (saved.isPinnedForEveryone()) {
                            composeAndSendPinSync(saved.getId().toString(), roomId.toString(), userKey.toString(), sessionId, ViewManageEnum.PIN);
                        } else {
                            composeAndSendPinForEveryone(saved.getId().toString(), roomId.toString(), userKey.toString(), sessionId, ViewManageEnum.PIN);
                        }
                    })
                    // Offload the blocking execution to a dedicated thread pool
                    .subscribeOn(MUC_DB_SCHEDULER)
                    // Pass the saved entity downstream after the side-effect completes
                    .thenReturn(saved);
                });
    }

    /**
     * Unpins a message from a MUC room.
     * Evaluates personal pin deletion ownership first, then falls back to attempting a global room pin removal.
     */
    public Mono<Void> unpinMessage( UUID userKey, UUID groupId, UUID messageId, String sessionId) {
        // 1. Attempt to delete the requesting user's personal pin instance first
        return pinMucMessageRepository.deleteById_GroupIdAndId_MessageIdAndPinnedByAndPinnedForEveryoneIsFalse(groupId, messageId, userKey)
                .subscribeOn(MUC_DB_SCHEDULER)
                .flatMap(personalDeletedCount -> {
                    if (personalDeletedCount > 0) {
                        log.debug("Successfully unpinned personal message {} from MUC room {}", messageId, groupId);
                        composeAndSendPinSync(messageId.toString(), groupId.toString(), userKey.toString(), sessionId, ViewManageEnum.UNPIN);
                        return Mono.<Void>empty();
                    }
                    
                    // 2. Fallback: If no personal pin matches, run the global channel cleanup query
                    return pinMucMessageRepository
                            .deleteById_GroupIdAndId_MessageIdAndPinnedForEveryoneIsTrue(groupId, messageId)
                            .flatMap(globalDeletedCount -> {
                                if (globalDeletedCount == 0) {
                                    return Mono.<Void>error(new PinMessageNotFoundException("Pinned MUC message not found."));
                                }
                                log.debug("Successfully unpinned global message {} from MUC room {}", messageId, groupId);
                                composeAndSendPinForEveryone(messageId.toString(), groupId.toString(), userKey.toString(), sessionId, ViewManageEnum.UNPIN);
                                return Mono.<Void>empty();
                            });
                })
                .doOnError(err -> log.error("Failed to remove MUC pin record for message {}", messageId, err))
                .then();
    }

    /**
     * Fetches all active pin definitions assigned within a single specific group channel.
     */
    public Flux<PinMucMessage> getPinnedMessagesForGroup(String groupId) {
        return pinMucMessageRepository.findById_GroupId(groupId)
                .subscribeOn(MUC_DB_SCHEDULER)
                .publishOn(MUC_DB_SCHEDULER) // Structural separation boundary for stream mappings
                .doOnError(err -> log.error("Failed to stream pins for MUC room: {}", groupId, err));
    }

    /**
     * Finds pinned messages matching your exact compound index structure inside a MUC space, ordered by seq ascending.
     * Evaluates Visibility boundary rules: matches either targeted user's personal pins OR global channel announcements.
     */
    public Flux<PinMucMessage> findPinnedMessages(UUID groupId, UUID pinnedBy) {    	
        return pinMucMessageRepository.findPinnedMessages(groupId, pinnedBy)
                .subscribeOn(MUC_DB_SCHEDULER)
                .publishOn(MUC_DB_SCHEDULER)
                .doOnError(err -> log.error("Error matching indexed pin search framework for user {} in MUC room {}", 
                        pinnedBy, groupId, err));
    }
    
    /**
	 * Generates a sync stanza to push the updated pin state to the MUC room context.
     * @return 
	 */
	private void composeAndSendPinSync(String targetId, String roomId, String userKey, String sessionId, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.room(roomId)				
				.from(jidUtil.getGroupBareJid(roomId) + "/" + userKey) // Add resource suffix targeting the user's presence inside the room
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, domainProperties.getDomain());
		Group group = groupCacheService.getCachedGroup(roomId);
		
		// Broadcast through the cluster message publisher bus for group chats
		mucMessageRouter.broadcastToOccupants(id, userKey, group, xml, sessionId);	
	}
	
	/**
	 * Generates a sync stanza to push the updated pin state out to other active multi-resource 
	 * client sessions belonging to the calling user (e.g., Mobile, Desktop apps).
	 */
	private void composeAndSendPinForEveryone(String targetId, String roomId, String userKey, String sessionId, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		PinStanza pinStanza = PinStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(jidUtil.getGroupBareJid(roomId) + "/" + userKey)
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(pinStanza.toXml(), stanzaId, domainProperties.getDomain());

		Group group = groupCacheService.getCachedGroup(roomId);
		// Broadcast through the cluster message publisher bus
		
		// Broadcast through the cluster message publisher bus for group chats
		mucMessageRouter.broadcastToOccupants(id, userKey, group, xml, sessionId);
	}
}
package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.PinChatMessage;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.ViewManageEnum;
import com.algomeet.xmpp.chatservice.exceptions.PinMessageNotFoundException;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.PinChatMessageRepository;
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
public class PinChatMessageService {

    private final PinChatMessageRepository pinChatMessageRepository;
    private final ClusterMessagePublisher reactiveClusterMessagePublisher;
    private final DomainProperties domainProperties;
    private final JidUtil jidUtil;

    // Scaled to 1,000 active threads and 50,000 queue bounds to safely absorb global broadcast spikes
    private static final Scheduler CHAT_WORKER_SCHEDULER = 
    		Schedulers.newBoundedElastic(
    				// Max Threads: Increased from 200 to accommodate rapid blocking repository calls and E2EE session lookups
    				1000, 
    				// Max Queue: Expanded from 10,000 to cleanly buffer cross-cluster XMPP pin synchronization payloads
    				50000, 
    				"xmpp-pin-message-workers"
    				);

    /**
     * Pins a new message inside a conversation context.
     */
    public Mono<PinChatMessage> pinMessage(UUID userKey, String sessionId, UUID peerKey, PinChatMessage pinChatMessage) {  	
        return pinChatMessageRepository.save(pinChatMessage)
                .subscribeOn(CHAT_WORKER_SCHEDULER)
                .doOnSuccess(saved -> log.debug("Successfully pinned message {} in conversation {}",                
                        saved.getId().getMessageId(), saved.getId().getConversationId()))
                .doOnError(err -> log.error("Failed to pin message due to database constraint", err))
                .flatMap(saved -> {
                    log.info("Executing pin: Message {} in direct chat with {} by user {}", saved.getId().getMessageId(), peerKey, userKey);
                    
                    // FIXED: Extracted raw message payload ID correctly instead of composite wrapper toString()
                    String targetMessageIdStr = saved.getId().getMessageId().toString();
                    
                    Mono<Void> broadcast = pinChatMessage.isPinnedForEveryone()
                            ? composeAndSendPinForEveryone(targetMessageIdStr, userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.PIN)
                            : composeAndSendSync(targetMessageIdStr, userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.PIN);
                            
                    return broadcast.thenReturn(saved);
                });
    }

    /**
     * Unpins a message by its conversation and specific unique payload message ID.
     */
    public Mono<Void> unpinMessage(UUID userKey, String sessionId, UUID peerKey, UUID messageId) {
        String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);

        return pinChatMessageRepository.deleteById_ConversationIdAndId_MessageIdAndId_PinnedByAndPinnedForEveryoneIsFalse(conversationId, messageId, userKey)
                .subscribeOn(CHAT_WORKER_SCHEDULER)
                .flatMap(personalDeletedCount -> {
                    if (personalDeletedCount > 0) {
                        log.debug("Successfully unpinned personal message {} from conversation {}", messageId, conversationId);
                        return composeAndSendSync(messageId.toString(), userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.UNPIN);
                    }
                    
                    return pinChatMessageRepository
                            .deleteById_ConversationIdAndId_MessageIdAndPinnedForEveryoneIsTrue(conversationId, messageId)
                            .flatMap(globalDeletedCount -> {
                                if (globalDeletedCount == 0) {
                                    return Mono.error(new PinMessageNotFoundException("Pinned message not found."));
                                }
                                log.debug("Successfully unpinned global message {} from conversation {}", messageId, conversationId);
                                return composeAndSendPinForEveryone(messageId.toString(), userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.UNPIN);
                            });
                })
                .doOnError(err -> log.error("Failed to remove pin record for message {}", messageId, err))
                .then(); 
    }

    /**
     * Finds pinned messages matching your exact compound index structure, ordered by seq ascending.
     */
    public Flux<PinChatMessage> findPinnedMessages(UUID userKey, UUID peerKey) {    	
        String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);
    	
        return pinChatMessageRepository.findPinnedMessages(conversationId, userKey)
                .subscribeOn(CHAT_WORKER_SCHEDULER)
                .doOnError(err -> log.error("Error matching indexed pin search framework for user {} in room {}", 
                		userKey, conversationId, err));
    }   
    
    /**
     * Generates a sync stanza to push the updated pin state out to other active multi-resource 
     * client sessions belonging to the calling user.
     */
    private Mono<Void> composeAndSendSync(String targetId, String userKey, String sessionId, String peerKey, ViewManageEnum viewManageEnum) {
        String id = UuidCreator.getTimeOrderedEpoch().toString();
        ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
                .id(id)
                .targetId(targetId)
                .from(jidUtil.getBareJid(userKey))
                .peer(peerKey)
                .action(viewManageEnum.getValue())
                .build();

        String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
        String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, domainProperties.getDomain());
        // Sync user's other devices by sending this message to itself
        return reactiveClusterMessagePublisher.convertAndSendToUser(
                id, userKey, userKey, ChatType.CHAT, false, false, xml, sessionId);
    }
	
    /**
     * Generates a sync stanza to push the updated pin state out globally.
     * Fires synchronization to both the initiator and the peer target context.
     */
    private Mono<Void> composeAndSendPinForEveryone(String targetId, String userKey, String sessionId, String peerKey, ViewManageEnum viewManageEnum) {
        String id = UuidCreator.getTimeOrderedEpoch().toString();
        PinStanza pinStanza = PinStanza.builder()
                .id(id)
                .targetId(targetId)
                .from(jidUtil.getBareJid(userKey))
                .action(viewManageEnum.getValue())
                .build();

        String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
        String xml = XmppStanzaUtil.insertStanzaId(pinStanza.toXml(), stanzaId, domainProperties.getDomain());
                
        // Notify the target recipient peer about the pin action event change
        return reactiveClusterMessagePublisher.convertAndSendToUser(
                id, peerKey, userKey, ChatType.CHAT, false, true, xml, sessionId);

    }
}
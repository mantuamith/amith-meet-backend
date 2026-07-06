package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.cluster.publisher.ReactiveClusterMessagePublisher;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PinChatMessageService {

    private final PinChatMessageRepository pinChatMessageRepository;
	private final ReactiveClusterMessagePublisher reactiveClusterMessagePublisher;
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;

    /**
     * Pins a new message inside a conversation context.
     */
    public Mono<PinChatMessage> pinMessage(UUID userKey, String sessionId, UUID peerKey, PinChatMessage pinChatMessage) {  	

        return pinChatMessageRepository.save(pinChatMessage)
                // 1. Log success/errors of the DB operation using side-effect operators
                .doOnSuccess(saved -> log.debug("Successfully pinned message {} in conversation {}",                
                        saved.getId().getMessageId(), saved.getId().getConversationId()))
                .doOnError(err -> log.error("Failed to pin message due to database constraint", err))
                
                // 2. Use flatMap to chain the next reactive operation
                .flatMap(saved -> {
                    log.info("Executing pin: Message {} in direct chat with {} by user {}", saved.getId(), peerKey, userKey);
                    
                    // Chain the cluster sync operation, then return the originally saved entity
                    if(pinChatMessage.isPinnedForEveryone()) {
                    	// Pin for everyone
                    	return composeAndSendPinForEveryone(saved.getId().toString(), userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.PIN)
                                .thenReturn(saved); 
                    } else {
                    	// Personal Pin
                    	return composeAndSendSync(saved.getId().toString(), userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.PIN)
                                .thenReturn(saved); 
                    }
                });
    }

    /**
     * Unpins a message by its conversation and specific unique payload message ID.
     */
    /**
     * Unpins a message by its conversation and specific unique payload message ID.
     * Evaluates ownership first (deleting personal pin) and falls back to clearing a global pin
     * if the requesting user didn't pin it personally.
     */
    public Mono<Void> unpinMessage(UUID userKey, String sessionId, UUID peerKey, UUID messageId) {
        String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);

        // 1. Attempt to delete the personal pin first
        return pinChatMessageRepository.deleteById_ConversationIdAndId_MessageIdAndId_PinnedByAndPinnedForEveryoneIsFalse(conversationId, messageId, userKey)
                .flatMap(personalDeletedCount -> {
                    // If a personal pin was matched and deleted, exit the chain early
                    if (personalDeletedCount > 0) {
                        log.debug("Successfully unpinned personal message {} from conversation {}", messageId, conversationId);
                        
                        return composeAndSendSync(messageId.toString(), userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.UNPIN);
                    }
                    
                    // 2. Fallback: If 0 personal pins were deleted, run the global deletion query
                    return pinChatMessageRepository
                            .deleteById_ConversationIdAndId_MessageIdAndPinnedForEveryoneIsTrue(conversationId, messageId)
                            .flatMap(globalDeletedCount -> {
                                if (globalDeletedCount == 0) {
                                    return Mono.<Void>error(new PinMessageNotFoundException("Pinned message not found."));
                                }
                                log.debug("Successfully unpinned global message {} from conversation {}", messageId, conversationId);
                                
                                return composeAndSendPinForEveryone(messageId.toString(), userKey.toString(), sessionId, peerKey.toString(), ViewManageEnum.UNPIN);
                            });
                })
                .doOnError(err -> log.error("Failed to remove pin record for message {}", messageId, err))
                .then(); // Guarantees type-safety return of Mono<Void>
    }

	/**
     * Finds pinned messages matching your exact compound index structure, ordered by seq ascending.
     * Matches: conversationId AND (pinnedBy OR pinnedForEveryone == true)
     * Sorts: { 'seq': 1 } (1 = Ascending, -1 = Descending)
     */
    public Flux<PinChatMessage> findPinnedMessages(UUID userKey, UUID peerKey, UUID pinnedBy) {    	
    	String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);
    	
        return pinChatMessageRepository.findPinnedMessages(conversationId, pinnedBy)
                .doOnError(err -> log.error("Error matching indexed pin search framework for user {} in room {}", 
                        pinnedBy, conversationId, err));
    }   
    
	/**
	 * Generates a sync stanza to push the updated pin state out to other active multi-resource 
	 * client sessions belonging to the calling user (e.g., Mobile, Desktop apps).
	 */
	private Mono<Void> composeAndSendSync(String targetId, String userKey, String sessionId, String peerKey, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(jidUtil.getBareJid(userKey))
				.to(jidUtil.getBareJid(userKey)) 
				.peer(peerKey)
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, domainProperties.getDomain());

		// Broadcast through the cluster message publisher bus
		return reactiveClusterMessagePublisher.convertAndSendToUser(id, userKey, userKey, 
				ChatType.CHAT, false, false, xml, sessionId);
	}
	
	/**
	 * Generates a sync stanza to push the updated pin state out to other active multi-resource 
	 * client sessions belonging to the calling user (e.g., Mobile, Desktop apps).
	 */
	private Mono<Void> composeAndSendPinForEveryone(String targetId, String userKey, String sessionId, String peerKey, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		PinStanza pinStanza = PinStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(jidUtil.getBareJid(userKey))
				.to(jidUtil.getBareJid(peerKey)) 
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(pinStanza.toXml(), stanzaId, domainProperties.getDomain());

		// Broadcast through the cluster message publisher bus
		return reactiveClusterMessagePublisher.convertAndSendToUser(id, userKey, userKey, 
				ChatType.CHAT, false, true, xml, sessionId);
	}
}
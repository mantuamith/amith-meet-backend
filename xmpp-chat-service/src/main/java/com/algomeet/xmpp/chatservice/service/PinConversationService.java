package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.PinConversation;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.ConversationViewAction;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.PinConversationRepository;
import com.algomeet.xmpp.chatservice.stanza.ConversationViewSyncStanza;
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
public class PinConversationService {
    private final PinConversationRepository pinConversationRepository;
    private final ClusterMessagePublisher reactiveClusterMessagePublisher;
    private final DomainProperties domainProperties;
    private final JidUtil jidUtil;

    /**
     * Retrieves all pinned conversations for a specific user, ordered by sequence ascending.
     *
     * @param userKey The UUID of the user who pinned the conversations.
     * @return Flux of PinConversation items.
     */
    public Flux<PinConversation> getPinnedConversations(UUID userKey) {
        log.debug("Fetching pinned conversations for user: {}", userKey);
        return pinConversationRepository.findPinnedConversation(userKey)
                .doOnError(ex -> log.error("Error fetching pinned conversations for user: {}", userKey, ex));
    }

    /**
     * Pins a conversation for a user. If already pinned, updates or returns existing.
     *
     * @param pinConversation The PinConversation document to save.
     * @return Mono containing the saved PinConversation.
     */
    public Mono<PinConversation> pinConversation(PinConversation pinConversation, String sessionId) {
    	log.debug("Pinning conversation: {}", pinConversation);

    	return pinConversationRepository.save(pinConversation)
    			.flatMap(saved -> {
    				String userKey = saved.getId().getPinnedBy().toString();
    				String peerKeyStr = saved.getPeerKey() != null ? saved.getPeerKey().toString() : null;
    				String roomIdStr = saved.getGroupId() != null ? saved.getGroupId().toString() : null;

    				return composeAndSendSync(userKey, sessionId, peerKeyStr, roomIdStr, ConversationViewAction.PIN)
    						.doOnError(ex -> log.error("Failed to send sync stanza for pinned conversation: {}", saved.getId(), ex))
    						.doOnSuccess(v -> log.info("Successfully sent sync stanza for conversation pin ID: {}", saved.getId()))
    						.thenReturn(saved);
    			})
    			.doOnError(ex -> log.error("Failed to pin conversation: {}", pinConversation, ex));
    }

    /**
     * Unpins a conversation for a specific user.
     *
     * @param conversationId The ID of the conversation to unpin.
     * @param userKey         The UUID of the user who owns the pin.
     * @return Mono<Boolean> true if deleted (deleted count > 0), false otherwise.
     */
    public Mono<Boolean> unpinConversation(UUID userKey, UUID peerKey, UUID groupId, String sessionId) {
        UUID conversationId = peerKey != null ? peerKey : groupId;
        log.debug("Unpinning conversationId: {} for user: {}", conversationId, userKey);

        String peerKeyStr = peerKey != null ? peerKey.toString() : null;
        String groupIdStr = groupId != null ? groupId.toString() : null;

        return pinConversationRepository.deleteById_ConversationIdAndId_PinnedBy(conversationId, userKey)
                .flatMap(deletedCount -> {
                    boolean deleted = deletedCount > 0;
                    if (!deleted) {
                        log.warn("No pinned conversation found to delete for conversationId: {} and user: {}", conversationId, userKey);
                        return Mono.just(false);
                    }

                    log.info("Unpinned conversationId: {} for user: {}", conversationId, userKey);

                    // Properly chain sync sending into the reactive pipeline with UNPIN action
                    return composeAndSendSync(userKey.toString(), sessionId, peerKeyStr, groupIdStr, ConversationViewAction.UNPIN)
                            .doOnError(ex -> log.error("Failed to send unpin sync stanza for user: {}, conversationId: {}", userKey, conversationId, ex))
                            .onErrorComplete() // Ensures unpin success boolean is still returned even if sync broadcast fails
                            .thenReturn(true);
                })
                .doOnError(ex -> log.error("Error unpinning conversationId: {} for user: {}", conversationId, userKey, ex));
    } 
        
    /**
     * Constructs and broadcasts the headline synchronization stanza to all user devices.
     */
    private Mono<Void> composeAndSendSync(String userKey, String sessionId, String peerKey, String roomId, ConversationViewAction action) {
        String id = UuidCreator.getTimeOrderedEpoch().toString();

        ConversationViewSyncStanza.Builder syncBuilder = ConversationViewSyncStanza.builder()
                .id(id)
                .from(jidUtil.getBareJid(userKey))
                .action(action.getValue());

        if (peerKey != null) {
            syncBuilder.peerKey(peerKey);
        }
        if (roomId != null) {
            syncBuilder.roomId(roomId);
        }

        ConversationViewSyncStanza syncStanza = syncBuilder.build();

        String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();
        String xml = XmppStanzaUtil.insertStanzaId(syncStanza.toXml(), stanzaId, domainProperties.getDomain());

        // Target user's bare JID so cluster publisher fans out to all active sessions/devices of the user
        return reactiveClusterMessagePublisher.convertAndSendToUser(
                id, userKey, userKey, ChatType.CHAT, false, false, xml, sessionId);
    }
}
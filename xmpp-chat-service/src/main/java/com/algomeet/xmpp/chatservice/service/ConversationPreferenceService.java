package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.ConversationPreference;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.ConversationViewAction;
import com.algomeet.xmpp.chatservice.repository.ConversationPreferenceRepository;
import com.algomeet.xmpp.chatservice.stanza.ConversationViewSyncStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationPreferenceService {
    private final ConversationPreferenceRepository conversationPreferenceRepository;
    private final ClusterMessagePublisher reactiveClusterMessagePublisher;
    private final JidUtil jidUtil;

    /**
     * Saves the preference document if any state flag (pinned, muted, archived) is true.
     * Otherwise, deletes the document from MongoDB to keep the collection lean.
     * 
     * @param pref the preference entity to inspect and persist/delete
     * @return Mono<Void> completing when DB operation finishes
     */
    public Mono<Void> saveOrCleanUp(ConversationPreference pref) {
        if (pref == null || pref.getId() == null) {
            return Mono.empty();
        }

        boolean active = (pref.getPinned() != null && pref.getPinned()) 
        		|| (pref.getMuted() != null && pref.getMuted()) 
        		|| (pref.getArchived() != null && pref.getArchived());

        if (active) {
            log.debug("Saving updated preferences for userKey: {}, conversationId: {}", 
                    pref.getId().getUserKey(), pref.getId().getConversationId());
            return conversationPreferenceRepository.save(pref).then();
        } else {
            log.debug("No active preference flags remain. Cleaning up record for userKey: {}, conversationId: {}", 
                    pref.getId().getUserKey(), pref.getId().getConversationId());
            return conversationPreferenceRepository.deleteById(pref.getId());
        }
    }
    
    /**
     * Constructs and broadcasts the headline synchronization stanza to all user devices.
     */
    public Mono<Void> composeAndSendSync(String userKey, String sessionId, String peerKey, String roomId, ConversationViewAction action) {
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

        // Target user's bare JID so cluster publisher fans out to all active sessions/devices of the user
        return reactiveClusterMessagePublisher.convertAndSendToUser(
                id, userKey, userKey, ChatType.CHAT, false, false, syncStanza.toXml(), sessionId);
    }
    
    public Flux<ConversationPreference> getConversationPreferences(UUID userKey) {
    	log.info("userKey={}", userKey);
    	return conversationPreferenceRepository.findById_UserKeyOrderByPinnedSeqAsc(userKey);
    }
    
}
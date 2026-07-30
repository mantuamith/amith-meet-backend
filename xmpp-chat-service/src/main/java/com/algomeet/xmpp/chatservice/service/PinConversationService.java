package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.ConversationPreference;
import com.algomeet.xmpp.chatservice.document.ConversationPreferenceId;
import com.algomeet.xmpp.chatservice.enums.ConversationViewAction;
import com.algomeet.xmpp.chatservice.repository.ConversationPreferenceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PinConversationService {
	private final ConversationPreferenceService convPreferenceService;
    private final ConversationPreferenceRepository conversationPreferenceRepository;

    /**
     * Retrieves all pinned conversations for a specific user, ordered by sequence ascending.
     *
     * @param userKey The UUID of the user who pinned the conversations.
     * @return Flux of ConversationPreference items.
     */
    public Flux<ConversationPreference> getPinnedConversations(UUID userKey) {
        log.debug("Fetching pinned conversations for user: {}", userKey);
        return conversationPreferenceRepository.findById_UserKeyOrderByPinnedSeqAsc(userKey)
                .doOnError(ex -> log.error("Error fetching pinned conversations for user: {}", userKey, ex));
    }

    /**
     * Pins a conversation for a user. If already pinned, updates or returns existing.
     *
     * @param pinConversation The ConversationPreference document to save.
     * @return Mono containing the saved ConversationPreference.
     */
    public Mono<ConversationPreference> pinConversation(ConversationPreference pinConversation, String sessionId) {
        log.debug("Pinning conversation: {}", pinConversation);

        return conversationPreferenceRepository.findById(pinConversation.getId())
            .flatMap(existing -> {
                // Update fields on the existing document so we preserve flags like 'muted' or 'archived'             
            	existing.setPinned(pinConversation.getPinned());
            	existing.setPinnedSeq(pinConversation.getPinnedSeq());
            	existing.setPinnedAt(pinConversation.getPinnedAt());
            	existing.setUpdatedAt(Instant.now());

            	return conversationPreferenceRepository.save(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // If record doesn't exist yet, set timestamp and save as a new document
                if (pinConversation.getCreatedAt() == null) {
                    pinConversation.setCreatedAt(Instant.now());
                }
                return conversationPreferenceRepository.save(pinConversation);
            }))
            .flatMap(saved -> {
                String userKey = saved.getId().getUserKey().toString();
                String peerKeyStr = saved.getPeerKey() != null ? saved.getPeerKey().toString() : null;
                String roomIdStr = saved.getGroupId() != null ? saved.getGroupId().toString() : null;

                return convPreferenceService.composeAndSendSync(userKey, sessionId, peerKeyStr, roomIdStr, ConversationViewAction.PIN)
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
        ConversationPreferenceId prefId = new ConversationPreferenceId(userKey, conversationId);

        return conversationPreferenceRepository.findById(prefId)
            .flatMap(convPreference -> {
                // Early return if it's already not pinned
                if (convPreference.getPinned() == null || !convPreference.getPinned()) {
                    return Mono.just(true);
                }

                convPreference.setPinned(false);
                convPreference.setPinnedSeq(null);
                convPreference.setPinnedAt(null);
                convPreference.setUpdatedAt(Instant.now());

                // Single centralized call: saves if still muted/archived, deletes if totally clear
                return convPreferenceService.saveOrCleanUp(convPreference)
                    .then(convPreferenceService.composeAndSendSync(userKey.toString(), sessionId, peerKeyStr, groupIdStr, ConversationViewAction.UNPIN))
                    .doOnError(ex -> log.error("Failed to send unpin sync stanza for user: {}, conversationId: {}", userKey, conversationId, ex))
                    .onErrorComplete()
                    .thenReturn(true);
            })
            .defaultIfEmpty(false);
    }    
}
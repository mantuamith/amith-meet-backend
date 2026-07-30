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
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MuteConversationService {
	private final ConversationPreferenceService convPreferenceService;
    private final ConversationPreferenceRepository conversationPreferenceRepository;

    /**
     * Mutes a conversation for a user. If already archived, updates or returns existing.
     *
     * @param muteConversation The ConversationPreference document to save.
     * @return Mono containing the saved ConversationPreference.
     */
    public Mono<ConversationPreference> muteConversation(ConversationPreference muteConversation, String sessionId) {
        log.debug("Muting conversation: {}", muteConversation);

        return conversationPreferenceRepository.findById(muteConversation.getId())
            .flatMap(existing -> {       
            	existing.setMuted(muteConversation.getMuted());
            	existing.setMuteUntil(muteConversation.getMuteUntil());                	
            	existing.setUpdatedAt(Instant.now());

            	return conversationPreferenceRepository.save(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // If record doesn't exist yet, set timestamp and save as a new document
                if (muteConversation.getCreatedAt() == null) {
                	muteConversation.setCreatedAt(Instant.now());
                }
                return conversationPreferenceRepository.save(muteConversation);
            }))
            .flatMap(saved -> {
                String userKey = saved.getId().getUserKey().toString();
                String peerKeyStr = saved.getPeerKey() != null ? saved.getPeerKey().toString() : null;
                String roomIdStr = saved.getGroupId() != null ? saved.getGroupId().toString() : null;

                return convPreferenceService.composeAndSendSync(userKey, sessionId, peerKeyStr, roomIdStr, ConversationViewAction.MUTE)
                        .doOnError(ex -> log.error("Failed to send sync stanza for muted conversation: {}", saved.getId(), ex))
                        .doOnSuccess(v -> log.info("Successfully sent sync stanza for conversation mute ID: {}", saved.getId()))
                        .thenReturn(saved);
            })
            .doOnError(ex -> log.error("Failed to pin conversation: {}", muteConversation, ex));
    }

    /**
     * Unmutes a conversation for a specific user.
     *
     * @param conversationId The ID of the conversation to unpin.
     * @param userKey         The UUID of the user who owns the pin.
     * @return Mono<Boolean> true if deleted (deleted count > 0), false otherwise.
     */
    public Mono<Boolean> unmuteConversation(UUID userKey, UUID peerKey, UUID groupId, String sessionId) {
        UUID conversationId = peerKey != null ? peerKey : groupId;
        log.debug("Unmuting conversationId: {} for user: {}", conversationId, userKey);

        String peerKeyStr = peerKey != null ? peerKey.toString() : null;
        String groupIdStr = groupId != null ? groupId.toString() : null;
        ConversationPreferenceId prefId = new ConversationPreferenceId(userKey, conversationId);

        return conversationPreferenceRepository.findById(prefId)
            .flatMap(convPreference -> {
                // Early return if it's already not pinned
                if (convPreference.getMuted() == null || !convPreference.getMuted()) {
                    return Mono.just(true);
                }

                convPreference.setMuted(false);
                convPreference.setMuteUntil(null);

                convPreference.setUpdatedAt(Instant.now());

                // Single centralized call: saves if still muted/archived, deletes if totally clear
                return convPreferenceService.saveOrCleanUp(convPreference)
                    .then(convPreferenceService.composeAndSendSync(userKey.toString(), sessionId, peerKeyStr, groupIdStr, ConversationViewAction.UNMUTE))
                    .doOnError(ex -> log.error("Failed to send unmute sync stanza for user: {}, conversationId: {}", userKey, conversationId, ex))
                    .onErrorComplete()
                    .thenReturn(true);
            })
            .defaultIfEmpty(false);
    }    
}
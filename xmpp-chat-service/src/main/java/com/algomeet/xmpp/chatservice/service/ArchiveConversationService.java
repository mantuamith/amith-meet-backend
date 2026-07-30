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
public class ArchiveConversationService {
	private final ConversationPreferenceService convPreferenceService;
    private final ConversationPreferenceRepository conversationPreferenceRepository;

    /**
     * Archives a conversation for a user. If already archived, updates or returns existing.
     *
     * @param archiveConversation The ConversationPreference document to save.
     * @return Mono containing the saved ConversationPreference.
     */
    public Mono<ConversationPreference> archiveConversation(ConversationPreference archiveConversation, String sessionId) {
        log.debug("Archiving conversation: {}", archiveConversation);

        return conversationPreferenceRepository.findById(archiveConversation.getId())
            .flatMap(existing -> {
                // Update fields on the existing document so we preserve flags like 'muted' or 'archived'               
            	existing.setArchived(archiveConversation.getArchived());
            	existing.setArchivedAt(archiveConversation.getArchivedAt());                	
            	existing.setUpdatedAt(Instant.now());

            	return conversationPreferenceRepository.save(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // If record doesn't exist yet, set timestamp and save as a new document
                if (archiveConversation.getCreatedAt() == null) {
                	archiveConversation.setCreatedAt(Instant.now());
                }
                return conversationPreferenceRepository.save(archiveConversation);
            }))
            .flatMap(saved -> {
                String userKey = saved.getId().getUserKey().toString();
                String peerKeyStr = saved.getPeerKey() != null ? saved.getPeerKey().toString() : null;
                String roomIdStr = saved.getGroupId() != null ? saved.getGroupId().toString() : null;

                return convPreferenceService.composeAndSendSync(userKey, sessionId, peerKeyStr, roomIdStr, ConversationViewAction.ARCHIVE)
                        .doOnError(ex -> log.error("Failed to send sync stanza for archived conversation: {}", saved.getId(), ex))
                        .doOnSuccess(v -> log.info("Successfully sent sync stanza for conversation archive ID: {}", saved.getId()))
                        .thenReturn(saved);
            })
            .doOnError(ex -> log.error("Failed to pin conversation: {}", archiveConversation, ex));
    }

    /**
     * Unarchives a conversation for a specific user.
     *
     * @param conversationId The ID of the conversation to unpin.
     * @param userKey         The UUID of the user who owns the pin.
     * @return Mono<Boolean> true if deleted (deleted count > 0), false otherwise.
     */
    public Mono<Boolean> unarchiveConversation(UUID userKey, UUID peerKey, UUID groupId, String sessionId) {
        UUID conversationId = peerKey != null ? peerKey : groupId;
        log.debug("Unpinning conversationId: {} for user: {}", conversationId, userKey);

        String peerKeyStr = peerKey != null ? peerKey.toString() : null;
        String groupIdStr = groupId != null ? groupId.toString() : null;
        ConversationPreferenceId prefId = new ConversationPreferenceId(userKey, conversationId);

        return conversationPreferenceRepository.findById(prefId)
            .flatMap(convPreference -> {
                // Early return if it's already not pinned
                if (convPreference.getArchived() == null || !convPreference.getArchived()) {
                    return Mono.just(true);
                }

                convPreference.setArchived(false);
                convPreference.setArchivedAt(null);

                convPreference.setUpdatedAt(Instant.now());

                // Single centralized call: saves if still muted/archived, deletes if totally clear
                return convPreferenceService.saveOrCleanUp(convPreference)
                    .then(convPreferenceService.composeAndSendSync(userKey.toString(), sessionId, peerKeyStr, groupIdStr, ConversationViewAction.UNARCHIVE))
                    .doOnError(ex -> log.error("Failed to send unarchive sync stanza for user: {}, conversationId: {}", userKey, conversationId, ex))
                    .onErrorComplete()
                    .thenReturn(true);
            })
            .defaultIfEmpty(false);
    }    
}
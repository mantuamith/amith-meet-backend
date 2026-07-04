package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.xmpp.chatservice.beans.OfflineMessageWithRetention;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;
import com.algomeet.xmpp.chatservice.util.XmppCustomRetentionStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppCustomStanzaUtil;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>Service responsible for the persistence and retrieval of XMPP stanzas 
 * that cannot be delivered immediately.</p>
 * 
 * <p>The {@code OfflineMessageService} plays a critical role in the <b>XEP-0160: 
 * Best Practices for Handling Offline Messages</b> implementation. It handles the 
 * "Store and Forward" logic required for asynchronous communication.</p>
 * 
 * <p><b>Key Operations:</b></p>
 * <ul>
 *     <li><b>Persistence:</b> Buffers stanzas into MongoDB when a recipient is unreachable.</li>
 *     <li><b>Sequential Retrieval:</b> Fetches messages in chronological order (First-In-First-Out) 
 *         to maintain conversation context upon reconnection.</li>
 *     <li><b>Reliable Cleanup:</b> Removes messages from the store only after receiving 
 *         a successful Stream Management (XEP-0198) acknowledgment from the client.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Service
@AllArgsConstructor
public class OfflineMessageService {	

    private final OfflineMessageRepository offlineMessageRepository;    
    private final ReactiveMongoTemplate mongoTemplate;
    private final ConversationSettingsCacheService conversationSettingsCacheService;
    private final ConversationSettingsFacade conversationSettingsFacade;
    
    /**
     * ersists a stanza as an offline document.
     * 
     * @param messageId
     * @param stanzaId
     * @param to
     * @param from
     * @param type
     * @param isAckStanza
     * @param isCountable
     * @param originalXml
     * @param mediaIds
     * @return A {@link Mono} emitting the saved {@link OfflineMessageWithRetention}.
     */
    public Mono<OfflineMessageWithRetention> save(UUID messageId, UUID stanzaId, String to, String from, String type, Boolean isAckStanza, 
            boolean isCountable, String originalXml, List<UUID> mediaIds) {
        UUID fromUuid = UUID.fromString(from);
        UUID toUuid = UUID.fromString(to);

        // 1. Fetch conversation settings reactively from the cache service
        return conversationSettingsCacheService.getCachedSettings(fromUuid, toUuid)
                .defaultIfEmpty(new ConversationSettings(Constants.UNLIMITED_MESSAGE_RETENTION_DAYS)) 
                .flatMap(conversationSettings -> {
                    
                    // Track your final resolved retention days across the reactive pipeline
                    int initialRetentionDays = conversationSettings.getMessageRetentionDays() != null 
                            ? conversationSettings.getMessageRetentionDays() 
                            : Constants.UNLIMITED_MESSAGE_RETENTION_DAYS;
                         
                    Integer parsedNewRetentionDays = null;
                    if (XmppCustomRetentionStanzaUtil.messageHasRetention(originalXml)) {
                        if (initialRetentionDays == Constants.UNLIMITED_MESSAGE_RETENTION_DAYS) {
                            String newRetentionDaysStr = null;
                            try {
                                newRetentionDaysStr = XmppCustomRetentionStanzaUtil.getMessageRetentionDays(originalXml);
                                parsedNewRetentionDays = Integer.parseInt(newRetentionDaysStr);
                            } catch(Exception ex) {
                                log.error("Error parsing retention days from payload {} ", newRetentionDaysStr, ex);
                            }
                        }
                    }

                    // If no update is required, continue with the initial value
                    if (parsedNewRetentionDays == null || parsedNewRetentionDays == Constants.UNLIMITED_MESSAGE_RETENTION_DAYS) {
                        return saveOfflineMessage(messageId, stanzaId, toUuid, fromUuid, type, isAckStanza, isCountable, originalXml, mediaIds, initialRetentionDays);
                    }

                    // Capture a final copy for the reactive stream down below
                    final int targetRetentionDays = parsedNewRetentionDays;

                    // 2. Safely update message retention through the facade
                    Mono<Integer> resolvedRetentionDaysMono = conversationSettingsFacade.updateMessageRetention(fromUuid, toUuid, targetRetentionDays)
                    		// If our update succeeds, pass our target retention days down the chain
                    		.thenReturn(targetRetentionDays)
                    		// If a lock collision happens, retrieve the updated configuration from the cache
                    		.onErrorResume(IllegalStateException.class, ex -> {
                    			log.warn("Lock collision encountered. Another process is updating the config. Fetching latest settings from cache...");

                    			return conversationSettingsCacheService.getCachedSettings(fromUuid, toUuid)
                    					// If the cache lookup comes up empty, default back to your original initial configuration
                    					.map(convSettings -> convSettings.getMessageRetentionDays() != null 
                    					? convSettings.getMessageRetentionDays() 
                    							: Constants.UNLIMITED_MESSAGE_RETENTION_DAYS)
                    					.defaultIfEmpty(initialRetentionDays);
                    		})
                    		// Catch-all block for any other unexpected database or infrastructure issues
                    		.onErrorResume(ex -> {
                    			log.error("Critical failure during retention update pipeline execution. Falling back to initial configuration: {}", initialRetentionDays, ex);
                    			return Mono.just(initialRetentionDays);
                    		});

                    // 3. Chain seamlessly into your saving logic using the dynamically resolved retention days
                    return resolvedRetentionDaysMono.flatMap(finalRetentionDays ->  {
                    	return saveOfflineMessage(messageId, stanzaId, toUuid, fromUuid, type, isAckStanza, isCountable, originalXml, mediaIds, finalRetentionDays);
                    });
                });
    }

    private Mono<OfflineMessageWithRetention> saveOfflineMessage(UUID messageId, UUID stanzaId, UUID toUuid, UUID fromUuid, String type, 
            Boolean isAckStanza, boolean isCountable, String originalXml, List<UUID> mediaIds, int retentionDays) {
        
        Instant purgeAt = (retentionDays == Constants.UNLIMITED_MESSAGE_RETENTION_DAYS)
                ? null
                : Instant.now().plus(retentionDays, java.time.temporal.ChronoUnit.DAYS);

        OfflineMessage offlineMessage = OfflineMessage.builder()
                .messageId(messageId)
                .stanzaId(stanzaId)
                .to(toUuid)
                .from(fromUuid)
                .messageType(type)
                .isAckStanza(isAckStanza)
                .countable(isCountable)
                .stanzaXml(originalXml)
                .mediaIds(mediaIds)
                .purgeAt(purgeAt)
                .build();
        
        OfflineMessageWithRetention result = new OfflineMessageWithRetention(offlineMessage, retentionDays);

        return offlineMessageRepository.save(offlineMessage).thenReturn(result);
    }
    
    /**
     * Persists a stanza as an offline document.
     * 
     * @param id          The unique Stanza ID.
     * @param to          The recipient's User Key or JID.
     * @param from        The sender's User Key or JID.
     * @param type        The XMPP message type (e.g., chat, groupchat).
     * @param originalXml The raw XML payload to be stored.
     * @return A {@link Mono} emitting the saved {@link OfflineMessage}.
     */
    public Mono<OfflineMessage> save(UUID messageId, UUID stanzaId, String to, String from, String type, String originalXml) {
    	UUID fromUuid = UUID.fromString(from);
    	UUID toUuid = UUID.fromString(to);

    	// 1. Fetch conversation settings reactively from the cache service
    	return conversationSettingsCacheService.getCachedSettings(fromUuid, toUuid)
    			// 2. Map or fallback to a default if settings are empty (e.g. conversation doesn't exist yet)
    			.defaultIfEmpty(new ConversationSettings(-1)) 
    			// 3. Pipeline the resolved settings to compute the retention time and save the message
    			.flatMap(conversationSettings -> {

    				// FIXED: Changed TemporalUnit.DAY to ChronoUnit.DAYS
    				Instant purgeAt = (conversationSettings.getMessageRetentionDays() != null && conversationSettings.getMessageRetentionDays() != -1) 
    						? Instant.now().plus(conversationSettings.getMessageRetentionDays(), ChronoUnit.DAYS)
    								: null;
    				OfflineMessage offlineMessage = OfflineMessage.builder()
    						.messageId(messageId)
    						.stanzaId(stanzaId)
    						.to(UUID.fromString(to))
    						.from(UUID.fromString(from))
    						.messageType(type)
    						.countable(XmppCustomStanzaUtil.isCountableMessage(originalXml))
    						.stanzaXml(originalXml)
    						.purgeAt(purgeAt)
    						.build();

    				return offlineMessageRepository.save(offlineMessage);
    			});
    }
    
    /**
     * Retrieves all buffered messages for a specific user, sorted by creation time.
     * 
     * @param to The User Key/JID of the recipient.
     * @return A list of {@link OfflineMessage} objects in the order they were originally sent.
     */
    public Flux<OfflineMessage> getOfflineMessages(UUID to) {
        return offlineMessageRepository.findByToAndDeliveredAtIsNullOrderByStanzaIdAsc(to);
    }
    
    /**
     * Performs a highly efficient, atomic soft-delete on an offline message document.
     * Instead of dropping the document entirely, this nullifies the heavy XML payload
     * to optimize database storage while preserving metadata for stream state audit trails.
     *
     * @param to        The recipient's user key / JID identifier.
     * @param messageId The unique UUID of the XMPP stanza.
     * @return A Mono signals completion (Void) once the database write is acknowledged.
     */
    public Mono<Void> clearOfflineStanza(UUID to, UUID messageId) {
        // Leverage your compound index {'to': 1, 'id': 1} for an efficient O(1) look-up
        Query query = Query.query(
            Criteria.where("to").is(to)
                    .and("messageId").is(messageId)
        );

        // Atomically clear the raw XML payload to reclaim storage and record the delivery timestamp.
        // The document is retained because it is still required for unread message count reconciliation.
        Update update = new Update()
                .set("stanzaXml", null)
                .set("deliveredAt", Instant.now());

        return mongoTemplate.updateFirst(query, update, OfflineMessage.class)
                .flatMap(updateResult -> {
                    if (updateResult.getMatchedCount() == 0) {
                        // Optional: Log a warning or handle a missing message case gracefully
                        return Mono.empty();
                    }
                    return Mono.empty();
                })
                .then();
    }
    
    /**
     * Deletes a messages from the persistent store after successful delivery.
     * 
     * <p>This is typically called when an {@code <a h='...'/>} acknowledgment 
     * is processed by the {@code XmppStreamManagementStanzaHandler}.</p>
     * 
     * @param messageIds The list of unique Stanza ID to be removed.
     * @return A {@link Mono<Void>} signaling completion of the deletion.
     */
    public Mono<Void> deleteAllByIds(List<UUID> messageIds) {
        return offlineMessageRepository.deleteAllById(messageIds);
    }
        
    public Mono<OfflineMessage> findByMessageIdAndSender(UUID id, UUID sender) {
        // Ensuring we check both ID and the 'from' JID for security
        return offlineMessageRepository.findByMessageIdAndFromAndDeliveredAtIsNull(id, sender);
    }
    
    public Mono<OfflineMessage> save(OfflineMessage message) {
    	return offlineMessageRepository.save(message);
    }
    
    /**
     * Hard-deletes (purges) all previously soft-deleted offline messages for a specific 
     * recipient up to a designated message checkpoint ID.
     * <p>
     * This method acts as a database garbage-collection routine, permanently removing 
     * records from the collection where {@code deletedAt} is already set (not null) 
     * and the message ID falls below the client-provided acknowledgment marker.
     * </p>
     *
     * @param to The receiver user key/ID whose processed offline message queue is being cleared.
     * @param from The Sender user key/ID whose processed offline message queue is being cleared.
     * @param id The upper bound message checkpoint ID (exclusive boundary; only IDs less than this are purged).
     * @return A {@code Mono<Void>} that signals completion when the matching records have been permanently deleted from MongoDB.
     */
    public Mono<Void> purgeDeletedMessagesUpToCheckpoint(UUID to, UUID from, UUID stanzaId){
    	return offlineMessageRepository.deleteByToAndFromAndStanzaIdLessThanEqualAndDeliveredAtIsNotNull(to, from, stanzaId);
    }
}
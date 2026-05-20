package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
@Service
@AllArgsConstructor
public class OfflineMessageService {	

    private final OfflineMessageRepository offlineMessageRepository;    
    private final ReactiveMongoTemplate mongoTemplate;
    
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
    public Mono<OfflineMessage> save(UUID id, String to, String from, String type, Boolean isAckStanza, String originalXml) {
        OfflineMessage offlineMessage = OfflineMessage.builder()
                .id(id)
                .to(UUID.fromString(to))
                .from(UUID.fromString(from))
                .messageType(type)
                .isAckStanza(isAckStanza)
                .stanzaXml(originalXml)
                .build();
        
        return offlineMessageRepository.save(offlineMessage);
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
    public Mono<OfflineMessage> save(UUID id, String to, String from, String type, String originalXml) {
        OfflineMessage offlineMessage = OfflineMessage.builder()
                .id(id)
                .to(UUID.fromString(to))
                .from(UUID.fromString(from))
                .messageType(type)
                .stanzaXml(originalXml)
                .build();
        
        return offlineMessageRepository.save(offlineMessage);
    }
    
    /**
     * Retrieves all buffered messages for a specific user, sorted by creation time.
     * 
     * @param to The User Key/JID of the recipient.
     * @return A list of {@link OfflineMessage} objects in the order they were originally sent.
     */
    public Flux<OfflineMessage> getOfflineMessages(UUID to) {
        return offlineMessageRepository.findByToAndDeletedAtIsNullOrderByIdAsc(to);
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
                    .and("id").is(messageId)
        );

        // Atomically nullify the raw XML payload to reclaim space and mark deletion time
        Update update = new Update()
                .set("stanzaXml", null)
                .set("deletedAt", Instant.now());

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
        
    public Mono<OfflineMessage> findByIdAndSender(UUID id, UUID sender) {
        // Ensuring we check both ID and the 'from' JID for security
        return offlineMessageRepository.findByIdAndFromAndDeletedAtIsNull(id, sender);
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
    public Mono<Void> purgeDeletedMessagesUpToCheckpoint(UUID to, UUID from, UUID id){
    	return offlineMessageRepository.deleteByToAndFromAndIdLessThanEqualAndDeletedAtIsNotNull(to, from, id);
    }
}
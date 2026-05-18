package com.algomeet.xmpp.chatservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;

import lombok.AllArgsConstructor;
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
@Service
@AllArgsConstructor
public class OfflineMessageService {	

    private final OfflineMessageRepository offlineMessageRepository;
    
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
                .to(to)
                .from(from)
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
    public Flux<OfflineMessage> getOfflineMessages(String to) {
        return offlineMessageRepository.findByToOrderByIdAsc(to);
    }
    
    /**
     * Purges a batch of pending offline messages up to a specific tracking boundary
     * after verification of successful client delivery.
     * 
     * <p>This is typically triggered sequentially when an {@code <a h='...'/>} 
     * stream management acknowledgment is processed by the {@code XmppStreamManagementStanzaHandler},
     * allowing the server to safely clear out historical queue records that the client 
     * has confirmed receiving.</p>
     * 
     * @param to        The target recipient's routing key or JID whose offline queue is being cleared.
     * @param messageId The highest monotonic message identifier (UUIDv7) up to which 
     *                  records will be permanently purged (inclusive).
     * @return A {@link Mono<Void>} signaling asynchronous completion of the batch range deletion.
     */
    public Mono<Void> purgeOfflineQueueUpTo(String to, UUID messageId) {
        return offlineMessageRepository.deleteByToAndIdLessThan(to, messageId);
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
        
    public Mono<OfflineMessage> findByIdAndSender(String id, String sender) {
        // Ensuring we check both ID and the 'from' JID for security
        return offlineMessageRepository.findByIdAndFrom(id, sender);
    }
    
    public Mono<OfflineMessage> save(OfflineMessage message) {
    	return offlineMessageRepository.save(message);
    }
}
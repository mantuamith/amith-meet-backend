package com.algomeet.xmpp.chatservice.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.repository.projection.MessagePurgeView;
import com.algomeet.xmpp.chatservice.repository.projection.OfflineMessageView;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OfflineMessageRepository extends ReactiveMongoRepository<OfflineMessage, UUID> {
    
    // Use Flux for a stream of reactive results
    Flux<OfflineMessage> findByToAndDeliveredAtIsNullOrderByStanzaIdAsc(UUID to);
    
    // Use Mono<Void> for reactive deletion
    Mono<Void> deleteByTo(UUID to);
    
    Mono<OfflineMessage> findByMessageIdAndFromAndDeliveredAtIsNull(UUID id, UUID from);
    
    /**
     * Deletes all delivered and read messages
     */
    Mono<Void> deleteByToAndFromAndStanzaIdLessThanEqualAndDeliveredAtIsNotNull(UUID to, UUID from, UUID stanzaId);
    
    // Counts unread messages
    Mono<Long> countByToAndFromAndStanzaIdGreaterThanAndCountableTrue(UUID to, UUID from, UUID stanzaId);
    
    /**
     * Hard-deletes an offline message record matching the given ID 
     * only if its acknowledgement status (isAck) is set to true.
     *
     * @param id The unique message UUID checkpoint
     * @return A Mono<Void> signaling completion when the operation finishes
     */
    Mono<Void> deleteByMessageIdAndIsAckStanzaTrue(UUID id);
    
    Mono<OfflineMessageView> findOfflineMessageViewByMessageId(UUID id);
    
    Mono<OfflineMessageView> findByFromOrderByStanzaIdDesc(UUID from);
    
    Mono<Void> deleteByToAndFromAndDeliveredAtIsNotNullAndStanzaIdLessThanEqual(
    	    UUID to, 
    	    UUID from, 
    	    UUID stanzaId
    	);
    
    /**
	 * Select messages where purgeAt <= paramter date
	 * 
	 */
	Flux<MessagePurgeView> findByPurgeAtLessThanEqual(Instant purgeAt);
}
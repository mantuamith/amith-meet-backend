package com.algomeet.xmpp.chatservice.service;

import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.algomeet.common.redis.lock.ChatMessageRetentionLockManager;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;
import com.algomeet.xmpp.chatservice.util.XmppSyncStanzaComposer;
import com.github.f4b6a3.uuid.UuidCreator;
import com.mongodb.client.result.UpdateResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {
	private final OfflineMessageRepository offlineMessageRepository;
	private final UnreadCountService unreadCountService;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final DomainProperties domainProperties;
	private final ConversationSettingServiceImpl conversationSettingService;
	private final ChatMessageRetentionLockManager chatMessageRetentionLockManager;
	private final ConversationSettingsCacheService conversationSettingsCacheService;
	private final ReactiveMongoTemplate reactiveMongoTemplate; 

	public Mono<UnreadCount> timelineCutoff(UUID userKey, UUID peerKey, UUID cutoffMessageId, UUID cutoffStanzaId) {	    
		String senderKeyStr = userKey.toString();
		String receiverKeyStr = userKey.toString();

		// 1. Compose the XMPP payload used to synchronize the user's online devices with locally stored conversation state.
		String payload = XmppSyncStanzaComposer.createDirectClearanceStanza(
				domainProperties.getDomain(),
				peerKey.toString(), 
				cutoffStanzaId.toString()
				);

		// 2. Generate unique tracking identifier for cluster delivery routing
		String clusterMessageId = UuidCreator.getTimeOrderedEpoch().toString();

		// 3. Dispatch payload (If this is a blocking cluster call, see the warning below)
		clusterMessagePublisher.convertAndSendToUser(
				clusterMessageId,
				receiverKeyStr, 
				senderKeyStr, 
				ChatType.CHAT, 
				payload
				);    

		// 4. Chain the database and service operations reactively
		return offlineMessageRepository
				.deleteByToAndFromAndDeliveredAtIsNotNullAndStanzaIdLessThanEqual(userKey, peerKey, cutoffStanzaId)
				// .then() waits for the deletion to complete, then moves to the next Mono
				.then(Mono.defer(() -> unreadCountService.syncUnreadCountByStanzaId(peerKey, userKey, cutoffMessageId, cutoffStanzaId)));
	}

	public Mono<Void> applyMessageRetentionPolicy(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
	    Integer retentionDays = (messageRetentionDays != -1) ? messageRetentionDays : null;

	    return Mono.defer(() -> {
	        // 1. ATOMIC ACQUIRE: Attempt to acquire the distributed lock
	        ChatMessageRetentionLockManager.LockToken lockToken = 
	                chatMessageRetentionLockManager.acquireLock(userKey, peerKey);

	        // If the token comes back null, the lock was not acquired (another process is running)
	        if (lockToken == null) {
	            return Mono.error(new IllegalStateException("Could not acquire retention update lock. Process already running."));
	        }

	        // 2. EXECUTE THE PIPELINE
	        return conversationSettingService
	                .saveOrUpdateRetentionDays(userKey, peerKey, retentionDays)
	                .flatMap(savedSetting -> conversationSettingsCacheService.evictSettings(userKey, peerKey)
	                        .then(updatePurgeAtByToAndFrom(userKey, peerKey, retentionDays))
	                )
	                .then()
	                // 3. SAFE RELEASE: Guarantees lock token release using the Lua script regardless of outcome
	                .doFinally(signalType -> chatMessageRetentionLockManager.releaseLock(lockToken));
	    });
	}
	
	public Mono<Long> updatePurgeAtByToAndFrom(UUID to, UUID from, Integer messageRetentionDays) {
	    // Wrap each distinct logical branch inside a separate Criteria instance
		Query query = new Query(
			    new Criteria().orOperator(
			        new Criteria().andOperator(
			            Criteria.where(OfflineMessage.FIELD_TO).is(to),
			            Criteria.where(OfflineMessage.FIELD_FROM).is(from)
			        ),
			        new Criteria().andOperator(
			            Criteria.where(OfflineMessage.FIELD_TO).is(from),
			            Criteria.where(OfflineMessage.FIELD_FROM).is(to)
			        )
			    )
			);

	    // Fallback if retention days is null or explicit flag
	    if (messageRetentionDays == null || messageRetentionDays == -1) {
	        AggregationUpdate clearUpdate = AggregationUpdate.update().set(OfflineMessage.FIELD_PURGE_AT).toValue(null);
	        return reactiveMongoTemplate.updateMulti(query, clearUpdate, OfflineMessage.class)
	                .map(UpdateResult::getModifiedCount);
	    }

	    // Convert days to milliseconds for the calculation
	    long retentionMs = (long) messageRetentionDays * 86400000L;

	    // Direct BSON Aggregation Expression evaluating field rules database-side
	    AggregationUpdate pipelineUpdate = AggregationUpdate.update()
	        .set(OfflineMessage.FIELD_PURGE_AT)
	        .toValue(
	            new Document("$add", List.of(
	                new Document("$ifNull", List.of("$" + OfflineMessage.FIELD_CREATED_AT, "$$NOW")),
	                retentionMs
	            ))
	        );

	    return reactiveMongoTemplate.updateMulti(query, pipelineUpdate, OfflineMessage.class)
	            .map(UpdateResult::getModifiedCount);
	}
}

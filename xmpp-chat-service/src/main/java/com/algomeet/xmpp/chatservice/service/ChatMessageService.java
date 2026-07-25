package com.algomeet.xmpp.chatservice.service;

import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

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
	
	// FIXED: Ensure we use the Reactive cluster publisher definition
	private final ClusterMessagePublisher reactiveClusterMessagePublisher;
	private final DomainProperties domainProperties;
	private final ReactiveMongoTemplate reactiveMongoTemplate; 

	/**
	 * Synchronizes dynamic user timelines, updates unread stats, and issues cross-node cluster sync signals.
	 */
	public Mono<UnreadCount> timelineCutoff(UUID userKey, UUID peerKey, UUID cutoffMessageId, UUID cutoffStanzaId) {	    
		String userKeyStr = userKey.toString();

		// 1. Compose the XMPP payload used to synchronize the user's online devices
		String payload = XmppSyncStanzaComposer.createDirectClearanceStanza(
				domainProperties.getDomain(),
				peerKey.toString(), 
				cutoffStanzaId.toString()
				);

		String clusterMessageId = UuidCreator.getTimeOrderedEpoch().toString();

		// FIXED: Wrap the cluster delivery into the unified reactive chain so it executes safely
		Mono<Void> syncClusterMono = reactiveClusterMessagePublisher.convertAndSendToUser(
				clusterMessageId,
				userKeyStr,       // to: Target the calling user's multi-resource sessions
				userKeyStr,       // from: Sourced by themselves
				ChatType.CHAT, 
				false,            // isAllowEcho
				payload,
				null              // sessionId
				);    

		// 2. Chain operations sequentially using then() and thenMono deferral blocks
		return syncClusterMono
				.then(offlineMessageRepository.deleteByToAndFromAndDeliveredAtIsNotNullAndStanzaIdLessThanEqual(userKey, peerKey, cutoffStanzaId))
				.then(Mono.defer(() -> unreadCountService.syncUnreadCountByStanzaId(peerKey, userKey, cutoffMessageId, cutoffStanzaId)));
	}
	
	/**
	 * Bulk updates the 'purgeAt' tracking time constraints of offline messaging documents database-side.
	 */
	public Mono<Long> updatePurgeAtByToAndFrom(UUID to, UUID from, Integer messageRetentionDays) {
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

	    if (messageRetentionDays == null || messageRetentionDays == -1) {
	        AggregationUpdate clearUpdate = AggregationUpdate.update().set(OfflineMessage.FIELD_PURGE_AT).toValue(null);
	        return reactiveMongoTemplate.updateMulti(query, clearUpdate, OfflineMessage.class)
	                .map(UpdateResult::getModifiedCount);
	    }

	    long retentionMs = (long) messageRetentionDays * 86400000L;

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
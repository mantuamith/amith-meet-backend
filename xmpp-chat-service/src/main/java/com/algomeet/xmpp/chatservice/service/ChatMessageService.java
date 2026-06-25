package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.redis.lock.ChatMessageRetentionLockManager;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;
import com.algomeet.xmpp.chatservice.util.XmppSyncStanzaComposer;
import com.github.f4b6a3.uuid.UuidCreator;

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
			// If lock is not acquired, another process is already updating retention.
			if (chatMessageRetentionLockManager.isLocked(userKey, peerKey)) {
				return Mono.error(new IllegalStateException("Could not acquire retention update lock."));
			}

			return conversationSettingService
					.saveOrUpdateRetentionDays(userKey, peerKey, retentionDays)
					.flatMap(savedSetting -> conversationSettingsCacheService.evictSettings(userKey, peerKey)
							.then(offlineMessageRepository.updatePurgeAtByToAndFrom(
									userKey,
									peerKey,
									retentionDays))
							)
					.then();
		});
	}
}

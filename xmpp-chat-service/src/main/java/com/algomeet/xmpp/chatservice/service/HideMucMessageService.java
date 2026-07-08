package com.algomeet.xmpp.chatservice.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ReactiveClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.publisher.DeleteMessageMediaEventPublisher;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.stanza.ViewManageSyncStanza;
import com.algomeet.xmpp.chatservice.util.HidetUtil;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class HideMucMessageService {
	private final XmppArchiveService xmppArchiveService;
	private final ReactiveClusterMessagePublisher reactiveClusterMessagePublisher;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final HidetUtil hidetUtil;
	private final JidUtil jidUtil;
	private final DeleteMessageMediaEventPublisher messageMediaDeleteEventPublisher;
	private final DomainProperties domainProperties;

	private static final Scheduler DB_SCHEDULER = Schedulers.boundedElastic();

	public Mono<Void> hideMessageForUser(UUID userKey, UUID roomId, UUID targetMessageId, String sessionId, String requestMessageId) {
		return xmppArchiveService.findByMessageId(targetMessageId)
			// subscribeOn handles the subscription context for finding the message
			.subscribeOn(DB_SCHEDULER) 
			.flatMap(message -> {             	
				log.debug("Executing hide: Message {} in room {} by user {}", targetMessageId, roomId, userKey);
				
				// Ensure the actual update occurs on the DB scheduler
				return xmppArchiveService.hideMessageForUser(targetMessageId, userKey)
						.publishOn(DB_SCHEDULER) 
						.flatMap(updateResult -> {
							if (updateResult.getMatchedCount() == 0) {
				                log.warn("No message found to hide for ID: {}", targetMessageId);
				            }
							// FIX: Capturing the Mono return so it executes
							Mono<Void> groupSyncMono = composeAndSendGroupSync(
									targetMessageId.toString().trim(), 
									roomId.toString(), 
									userKey.toString(), 
									sessionId
							);

							Mono<Void> hideRelatedMono = hidetUtil.hideRelatedMessages(userKey, roomId, targetMessageId).then();

							Mono<Void> mediaDeleteMono = Mono.empty();
							if (!CollectionUtils.isEmpty(message.getMediaIds())) {
								mediaDeleteMono = messageMediaDeleteEventPublisher.publish(
										userKey.toString(), 
										message.getMediaIds().stream().map(UUID::toString).collect(Collectors.toSet()), 
										Set.of(userKey.toString()),
										null, 
										message.getMessageId().toString()
								).then();
							}

							// Combine group synchronization concurrently alongside deletion tasks
							return Mono.when(hideRelatedMono, mediaDeleteMono)
									.then(groupSyncMono)
									.then(sendIqResult(requestMessageId, userKey.toString()));
						});
			})
			.doOnError(err -> log.error("Error processing group chat hide context", err))
			.then(); 
	}

	public Mono<Void> composeAndSendDirectSync(String targetId, XmppPrincipal principal) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();

		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(principal.getBareJid())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		return reactiveClusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.CHAT, false, xml, principal);
	}

	private Mono<Void> composeAndSendGroupSync(String targetId, String roomId, String userKey, String sessionId) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();

		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.room(roomId)
				.from(jidUtil.getBareJid(userKey))
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, domainProperties.getDomain());

		XmppPrincipal principal = XmppPrincipal.builder()
				.sessionId(sessionId)
				.build();
		
		return reactiveClusterMessagePublisher.convertAndSendToUser(id, userKey, userKey, 
				ChatType.CHAT, false, xml, principal);
	}

	/**
	 * Sends a standard XMPP IQ 'result' stanza back to the user's local session.
	 *
	 * @param id      The unique ID of the original request stanza.
	 * @param userKey The unique key identifying the requesting user.
	 */
	public Mono<Void> sendIqResult(String id, String userKey) {
		if (id == null) {
			log.warn("Attempted to send IQ result with null/empty ID for user {}", userKey);
			return Mono.empty();
		}

		String iqResult = String.format("<iq type='result' id='%s'/>", id);
		log.debug("Dispatching IQ result: id={}, user={}", id, userKey);

		return localStanzaDispatcher.dispatchLocally(userKey, userKey, iqResult)
				.doOnError(err -> log.error("Failed to locally dispatch IQ result for user {}", userKey, err))
				.then();
	}
}
package com.algomeet.xmpp.chatservice.routing.view;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.publisher.DeleteMessageMediaEventPublisher;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.stanza.ViewManageSyncStanza;
import com.algomeet.xmpp.chatservice.stanza.parser.ViewManageStaxParser;
import com.algomeet.xmpp.chatservice.util.HidetUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class HideMessageHandler {
	
	// Dedicated thread pool for database work to prevent Netty thread starvation
	private static final Scheduler DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-chat-hide-db-workers");

	private final XmppArchiveService xmppArchiveService;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final HidetUtil hidetUtil;
	private final DeleteMessageMediaEventPublisher messageMediaDeleteEventPublisher;

	/**
	 * Logic to hide a message. Differentiates between MUC rooms and 1-on-1 chats.
	 */
	public void handleHide(ChannelHandlerContext ctx, String id, XmppPrincipal principal, ViewManageStaxParser.ViewItem item){
		if (StringUtils.hasText(item.room)) {
			// GROUP CHAT FLOW
			xmppArchiveService.findByMessageId(UUID.fromString(item.id))
			.subscribeOn(DB_SCHEDULER) // Offloads the initial DB fetch assembly/execution off Netty
			.publishOn(DB_SCHEDULER)   // Ensures subsequent processing runs on the DB thread pool
			.flatMap(message -> {             	

				log.info("Executing hide: Message {} in room {} by user {}", 
						item.id, item.room, principal.getUserKey());

				// Atomic update in MongoDB: add current user key to 'hiddenFromUserKeys'
				return xmppArchiveService.hideMessageForUser(message.getMessageId(), UUID.fromString(principal.getUserKey()))
						.flatMap(success -> {
							// Response to client
							sendIqResult(id, principal);				

							// Disseminate the change to user's other devices
							composeAndSendGroupSync(item.id.trim(), item.room, principal);      

							// Build the reactive pipeline for hiding related messages
							Mono<Void> hideRelatedMono = hidetUtil.hideRelatedMessages(
									UUID.fromString(principal.getUserKey()), 
									message.getRoomId(), 
									message.getMessageId()
							).then();

							// Build the reactive pipeline for media deletion if applicable
							Mono<Void> mediaDeleteMono = Mono.empty();
							if (!CollectionUtils.isEmpty(message.getMediaIds())) {
								mediaDeleteMono = messageMediaDeleteEventPublisher.publish(
										principal.getUserKey(), 
										message.getMediaIds().stream().map(UUID::toString).collect(Collectors.toSet()), 
										Set.of(principal.getUserKey()),
										null, 
										message.getMessageId().toString()
								).then();
							}

							// Combine asynchronous operations concurrently on the DB pool
							return Mono.when(hideRelatedMono, mediaDeleteMono);
						});
			})
			.doOnError(err -> log.error("Error processing group chat hide context", err))
			.subscribe(); // Single root subscription triggers the reactive pipeline execution safely

		} else {			
			// DIRECT CHAT FLOW
			composeAndSendDirectSync(item.id.trim(), principal);

			// Send response
			sendIqResult(id, principal);
		}
	}


	/**
	 * Syncs the 'hide' state for 1-on-1 messages to other resources of the user.
	 */
	private void composeAndSendDirectSync(String targetId, XmppPrincipal principal) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();

		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(principal.getBareJid())
				.to(principal.getBareJid()) // To self (Bare JID) triggers fan-out
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		// Push to the cluster for delivery to all active sessions for this user
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.CHAT, false, xml, principal);
	}

	/**
	 * Syncs the 'hide' state for MUC messages and archives the sync event if needed.
	 */
	private void composeAndSendGroupSync(String targetId, String roomJid, XmppPrincipal principal) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();

		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.room(roomJid)
				.from(principal.getBareJid())
				.to(roomJid + "/" + principal.getUserKey()) // MUC targeted to the specific user resource
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();

		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		// Publish to other active sessions
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.GROUPCHAT, false, xml, principal);

	}

	/**
	 * Sends a standard XMPP IQ 'result' stanza back to the user's local session.
	 * Used to acknowledge successful receipt and processing of a request.
	 *
	 * @param id        The unique ID of the original request stanza.
	 * @param principal The session context of the requesting user.
	 */
	public void sendIqResult(String id, XmppPrincipal principal) {
		if (id == null || id.isBlank()) {
			log.warn("Attempted to send IQ result with null/empty ID for user {}", principal.getUserKey());
			return;
		}

		String iqResult = String.format("<iq type='result' id='%s'/>", id);

		log.debug("Dispatching IQ result: id={}, user={}", id, principal.getUserKey());

		// Dispatching to the user's own key as both sender and receiver for local session acknowledgement
		localStanzaDispatcher.dispatchLocally(
				principal.getUserKey(), 
				principal.getUserKey(), 
				iqResult
				)
				.subscribeOn(DB_SCHEDULER) // Ensure dispatch subscription doesn't block calling threads
				.doOnError(err -> log.error("Failed to locally dispatch IQ result for user {}", principal.getUserKey(), err))
				.subscribe();
	}
}
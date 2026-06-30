package com.algomeet.xmpp.chatservice.routing.view;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.ViewManageEnum;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.service.PinChatMessageService;
import com.algomeet.xmpp.chatservice.service.PinMucMessageService;
import com.algomeet.xmpp.chatservice.stanza.ViewManageSyncStanza;
import com.algomeet.xmpp.chatservice.stanza.parser.PinMessageStaxParser.ParsedMessage;
import com.algomeet.xmpp.chatservice.stanza.parser.ViewManageStaxParser;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Component responsible for managing the unpin lifecycle of chat messages.
 * Handles database removal operations asynchronously using a dedicated worker pool
 * and ensures status synchronization across multi-client resources and the cluster.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpinMessageHandler {
	
	// Dedicated thread pool for heavy database work to protect the Netty I/O loops from starvation
	private static final Scheduler DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-unpin-message-workers");

	private final ClusterMessagePublisher clusterMessagePublisher;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final PinMucMessageService pinMucMessageService;
	private final PinChatMessageService pinChatMessageService;

	/**
	 * Main entry point to process an incoming client unpin instruction.
	 * Splits workflows dynamically based on whether the target message lives in a MUC room or direct chat.
	 * * @param ctx       The Netty channel context for the active TCP socket.
	 * @param id        The incoming stanza ID used to send back an IQ receipt confirmation.
	 * @param principal The session token containing user identification data.
	 * @param item      The parsed view item payload detailing what needs to be unpinned.
	 */
	public void handleUnpin(ChannelHandlerContext ctx, String id, XmppPrincipal principal, ViewManageStaxParser.ViewItem item){
		if (StringUtils.hasText(item.room)) {
			// GROUP CHAT FLOW
			log.info("Executing unpin: Message {} in room {} by user {}", item.id, item.room, principal.getUserKey());
			String groupId = XmppUtil.getRoomId(item.room);

			executeGroupUnpin(groupId, item.id, principal.getUserKey())
					.flatMap(unused -> {
						// Acknowledge the client session that requested the unpin
						sendIqResult(id, principal);				
						// Disseminate changes to user's other active multi-device endpoints inside the room
						composeAndSendGroupSync(item.id.trim(), item.room, principal, ViewManageEnum.UNPIN);   
						return Mono.empty();
					})
					// Safely handle downstream un-caught stream errors to avoid silent thread failures
					.subscribe(null, err -> log.error("Stream failure in group chat unpin workflow", err));

		} else {	
			// DIRECT CHAT FLOW
			log.info("Executing unpin: Message {} in direct chat with {} by user {}", item.id, item.peer, principal.getUserKey());
			String peerKey = XmppUtil.getUserKey(item.peer);
			
			executeDirectUnpin(principal.getUserKey(), peerKey, item.id)
					.flatMap(unused -> {		
						// Acknowledge client session over the TCP connection
						sendIqResult(id, principal);
						// Fan out the synchronization stanza to alternate local or remote cluster user devices
						composeAndSendDirectSync(item.id.trim(), principal, ViewManageEnum.UNPIN);
						return Mono.empty();
					})
					.subscribe(null, err -> log.error("Stream failure in direct chat unpin workflow", err));
		}
	}
	
	/**
	 * Fire-and-forget background worker variant to globally unpin a 1-on-1 direct chat message 
	 * for everyone in the thread.
	 */
	public void handleUnpinChatMessageForEveryone(String peerKey, ParsedMessage message, XmppPrincipal principal) {
		executeDirectUnpin(principal.getUserKey(), peerKey, message.id)
				.subscribe(
					unused -> log.debug("Successfully unpinned direct message for everyone"),
					err -> log.error("Failed executing background direct message unpin for everyone", err)
				);
	}

	/**
	 * Fire-and-forget background worker variant to globally unpin a MUC room message 
	 * for all members in the group.
	 */
	public void handleUnpinGroupMessageForEveryone(String groupId, ParsedMessage message, XmppPrincipal principal) {
		executeGroupUnpin(groupId, message.id, principal.getUserKey())
				.subscribe(
					unused -> log.debug("Successfully unpinned group message for everyone"),
					err -> log.error("Failed executing background group message unpin for everyone", err)
				);
	}

	/**
	 * Helper method to encapsulate direct chat database deletion details.
	 * Uses .subscribeOn to safely shift execution contexts out of Netty and onto the elastic DB pool.
	 */
	private Mono<?> executeDirectUnpin(String creatorKey, String peerKey, String targetMessageId) {
		return pinChatMessageService.unpinMessage(UUID.fromString(creatorKey), UUID.fromString(peerKey), UUID.fromString(targetMessageId))
				.subscribeOn(DB_SCHEDULER)
				.doOnError(err -> log.error("Failed handling direct chat un-pin lifecycle routing execution", err));
	}

	/**
	 * Helper method to encapsulate group chat database deletion details.
	 * Uses .subscribeOn to safely shift execution contexts out of Netty and onto the elastic DB pool.
	 */
	private Mono<?> executeGroupUnpin(String groupId, String targetMessageId, String creatorKey) {
		return pinMucMessageService.unpinMessage(UUID.fromString(groupId), UUID.fromString(targetMessageId), UUID.fromString(creatorKey))
				.subscribeOn(DB_SCHEDULER)
				.doOnError(err -> log.error("Failed handling group chat un-pin lifecycle routing execution", err));
	}

	/**
	 * Assembles and transmits a 1-on-1 state sync stanza out across the cluster broker.
	 * Setting 'to' to the user's Bare JID triggers multi-device fan-out routing internally.
	 */
	private void composeAndSendDirectSync(String targetId, XmppPrincipal principal, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(principal.getBareJid())
				.to(principal.getBareJid()) 
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.CHAT, false, xml, principal);
	}

	/**
	 * Assembles and transmits a MUC state sync stanza out across the cluster broker.
	 * Targets the current user's room presence resources explicitly.
	 */
	private void composeAndSendGroupSync(String targetId, String roomJid, XmppPrincipal principal, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.room(roomJid)
				.from(principal.getBareJid())
				.to(roomJid + "/" + principal.getUserKey()) 
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.GROUPCHAT, false, xml, principal);
	}

	/**
	 * Sends an XMPP IQ 'result' stanza back to the requesting client endpoint.
	 * Dispatched safely via the local node socket dispatcher structure.
	 */
	public void sendIqResult(String id, XmppPrincipal principal) {
		if (id == null || id.isBlank()) {
			log.warn("Attempted to send IQ result with null/empty ID for user {}", principal.getUserKey());
			return;
		}

		String iqResult = String.format("<iq type='result' id='%s'/>", id);
		log.debug("Dispatching IQ result: id={}, user={}", id, principal.getUserKey());

		localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), iqResult)
				.subscribeOn(DB_SCHEDULER) 
				.doOnError(err -> log.error("Failed to locally dispatch IQ result for user {}", principal.getUserKey(), err))
				.subscribe();
	}
}
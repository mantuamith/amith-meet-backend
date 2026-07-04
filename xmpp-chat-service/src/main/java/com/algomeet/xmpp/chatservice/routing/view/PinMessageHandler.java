package com.algomeet.xmpp.chatservice.routing.view;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.common.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.PinChatMessage;
import com.algomeet.xmpp.chatservice.document.PinChatMessageId;
import com.algomeet.xmpp.chatservice.document.PinMucMessage;
import com.algomeet.xmpp.chatservice.document.PinMucMessageId;
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
 * Handles the business logic for pinning both direct (1-on-1) and group (MUC) chat messages.
 * Manages database persistence off the main Netty event loop and triggers cluster-wide synchronization stanzas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PinMessageHandler {

	// Dedicated elastic scheduler for database interactions to prevent blocking Netty's I/O event loop threads
	private static final Scheduler DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-pin-message-workers");

	private final ClusterMessagePublisher clusterMessagePublisher;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final PinMucMessageService pinMucMessageService;
	private final PinChatMessageService pinChatMessageService;

	/**
	 * Primary entry point for routing a client-initiated pin request.
	 * Determines whether the message belongs to a MUC room or a direct chat.
	 *
	 * @param ctx       The Netty channel handler context for the current connection.
	 * @param id        The original XMPP stanza ID (used for responding with an IQ result).
	 * @param principal The authenticated user session metadata.
	 * @param item      The parsed details of the message to be pinned.
	 */
	public void handlePin(ChannelHandlerContext ctx, String id, XmppPrincipal principal, ViewManageStaxParser.ViewItem item){
		if (StringUtils.hasText(item.room)) {
			// GROUP CHAT (MUC) FLOW
			log.info("Executing pin: Message {} in room {} by user {}", item.id, item.room, principal.getUserKey());
			String groupId = XmppUtil.getRoomId(item.room);

			persistGroupPin(item.id, groupId, principal.getUserKey(), false)
				.flatMap(savedDoc -> {
					// Once recorded in database, acknowledge the requesting client
					sendIqResult(id, principal);				
					// Multi-device sync: Notify other cluster connected endpoints belonging to this user
					composeAndSendGroupSync(item.id.trim(), item.room, principal, ViewManageEnum.PIN);   
					return Mono.empty();
				})
				// Terminal subscription handling errors safely to prevent silent pipeline drop-offs
				.subscribe(null, err -> log.error("Stream failure in group chat pin workflow", err));

		} else {	
			// DIRECT CHAT (1-on-1) FLOW
			log.info("Executing pin: Message {} in direct chat with {} by user {}", item.id, item.peer, principal.getUserKey());
			String peerKey = XmppUtil.getUserKey(item.peer);
			
			persistDirectPin(item.id, peerKey, principal.getUserKey(), false)
				.flatMap(savedDoc -> {		
					// Acknowledge client session over TCP connection
					sendIqResult(id, principal);
					// Fan out changes down to matching user resource connections across the cluster
					composeAndSendDirectSync(item.id.trim(), principal, ViewManageEnum.PIN);
					return Mono.empty();
				})
				.subscribe(null, err -> log.error("Stream failure in direct chat pin workflow", err));
		}
	}

	/**
	 * Background worker endpoint processing administrative/global direct chat pin operations.
	 * Pins the message with 'pinnedForEveryone = true'.
	 */
	public void handlePinChatMessageForEveryone(String peerKey, ParsedMessage message, XmppPrincipal principal) {
		String targetId = message.id;
		
		persistDirectPin(targetId, peerKey, principal.getUserKey(), true)	    
			.subscribe(
				unused -> log.debug("Successfully processed direct message pin for everyone"),
				err -> log.error("Failed to execute background direct message pin for everyone", err)
			);
	}

	/**
	 * Background worker endpoint processing administrative/global room pin operations.
	 * Pins the message with 'pinnedForEveryone = true'.
	 */
	public void handlePinGroupMessageForEveryone(String groupId, ParsedMessage message, XmppPrincipal principal) {
		String targetId = message.id;

		persistGroupPin(targetId, groupId, principal.getUserKey(), true)
			.subscribe(
				unused -> log.debug("Successfully processed group message pin for everyone"),
				err -> log.error("Failed to execute background group message pin for everyone", err)
			);
	}

	/**
	 * Assembles and saves a direct chat pin record to the datastore.
	 * Uses subscribeOn to offload the reactive pipeline onto the dedicated database worker pool.
	 */
	private Mono<PinChatMessage> persistDirectPin(String targetMessageId, String peerKey, String creatorKey, boolean forEveryone) {
		// Generates an ordered, predictable conversation token based on sorting participant UUID values
		String conversationId = DeterministicConversationIdUtil.getConversationId(
				UUID.fromString(creatorKey), UUID.fromString(peerKey));

		PinChatMessage document = PinChatMessage.builder()
				.id(new PinChatMessageId(conversationId, UUID.fromString(targetMessageId), UUID.fromString(creatorKey)))
				.seq(UuidCreator.getTimeOrderedEpoch()) // Ensures chronological order sorting for view layers
				.pinnedForEveryone(forEveryone)
				.build();

		return pinChatMessageService.pinMessage(document)
				.subscribeOn(DB_SCHEDULER)
				.doOnError(err -> log.error("Failed handling direct chat pin lifecycle routing execution", err));    
	}

	/**
	 * Assembles and saves a group chat (MUC) room pin record to the datastore.
	 * Uses subscribeOn to offload the reactive pipeline onto the dedicated database worker pool.
	 */
	private Mono<PinMucMessage> persistGroupPin(String targetMessageId, String groupId, String creatorKey, boolean forEveryone) {
		PinMucMessage document = PinMucMessage.builder()
				.id(new PinMucMessageId(UUID.fromString(groupId), UUID.fromString(targetMessageId), UUID.fromString(creatorKey)))
				.seq(UuidCreator.getTimeOrderedEpoch())
				.pinnedForEveryone(forEveryone)
				.build();

		return pinMucMessageService.pinMessage(document)
				.subscribeOn(DB_SCHEDULER)
				.doOnError(err -> log.error("Failed handling group chat pin lifecycle routing execution", err));
	}

	/**
	 * Generates a sync stanza to push the updated pin state out to other active multi-resource 
	 * client sessions belonging to the calling user (e.g., Mobile, Desktop apps).
	 */
	private void composeAndSendDirectSync(String targetId, XmppPrincipal principal, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(principal.getBareJid())
				.to(principal.getBareJid()) // To self (Bare JID) instructs downstream routers to multi-cast across resources
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		// Broadcast through the cluster message publisher bus
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.CHAT, false, xml, principal);
	}

	/**
	 * Generates a sync stanza to push the updated pin state to the MUC room context.
	 */
	private void composeAndSendGroupSync(String targetId, String roomJid, XmppPrincipal principal, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();
		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.room(roomJid)
				.from(principal.getBareJid())
				.to(roomJid + "/" + principal.getUserKey()) // Add resource suffix targeting the user's presence inside the room
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		// Broadcast through the cluster message publisher bus for group chats
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.GROUPCHAT, false, xml, principal);
	}

	/**
	 * Delivers a standard raw XMPP IQ 'result' packet back down the specific TCP channel
	 * associated with the local user session to acknowledge successful tracking.
	 */
	public void sendIqResult(String id, XmppPrincipal principal) {
		if (id == null || id.isBlank()) {
			log.warn("Attempted to send IQ result with null/empty ID for user {}", principal.getUserKey());
			return;
		}

		String iqResult = String.format("<iq type='result' id='%s'/>", id);
		log.debug("Dispatching IQ result: id={}, user={}", id, principal.getUserKey());

		// Route the XML payload locally on the node handling this user connection socket
		localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), iqResult)
			.subscribeOn(DB_SCHEDULER) 
			.doOnError(err -> log.error("Failed to locally dispatch IQ result for user {}", principal.getUserKey(), err))
			.subscribe();
	}
}
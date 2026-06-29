package com.algomeet.xmpp.chatservice.routing.view;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
import com.algomeet.xmpp.chatservice.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
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
public class PinMessageHandler {
	// Dedicated thread pool for database work to prevent Netty thread starvation
	private static final Scheduler DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-pin-message-workers");

	private final ClusterMessagePublisher clusterMessagePublisher;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final PinMucMessageService pinMucMessageService;
	private final PinChatMessageService pinChatMessageService;

	/**
	 * Logic to pin a message. Differentiates between MUC rooms and 1-on-1 chats.
	 */
	public void handlePin(ChannelHandlerContext ctx, String id, XmppPrincipal principal, ViewManageStaxParser.ViewItem item){
		if (StringUtils.hasText(item.room)) {
			// GROUP CHAT FLOW
			log.info("Executing pin: Message {} in room {} by user {}", 
					item.id, item.room, principal.getUserKey());

			String groupId = XmppUtil.getRoomId(item.room);

			PinMucMessage document = PinMucMessage.builder()
					.id(new PinMucMessageId(UUID.fromString(groupId), UUID.fromString(item.id), UUID.fromString(principal.getUserKey())))
					.seq(UuidCreator.getTimeOrderedEpoch())
					.pinnedForEveryone(false)
					.build();

			pinMucMessageService.pinMessage(document)
			.subscribeOn(DB_SCHEDULER)
			.flatMap(savedDoc -> {
				// Acknowledge the client session
				sendIqResult(id, principal);				
				// Disseminate changes to user's other active cluster devices
				composeAndSendGroupSync(item.id.trim(), item.room, principal, ViewManageEnum.PIN);   
				return Mono.empty();
			})
			.doOnError(err -> log.error("Failed handling group chat pin lifecycle routing execution", err))
			.subscribe();

		} else {	
			// DIRECT CHAT FLOW
			log.info("Executing pin: Message {} in direct chat with {} by user {}", 
					item.id, item.peer, principal.getUserKey());

			String peerKey = XmppUtil.getUserKey(item.peer);
			String conversationId = DeterministicConversationIdUtil.getConversationId(
					UUID.fromString(principal.getUserKey()), UUID.fromString(peerKey));

			PinChatMessage document = PinChatMessage.builder()
					.id(new PinChatMessageId(conversationId, UUID.fromString(item.id), UUID.fromString(principal.getUserKey())))
					.seq(UuidCreator.getTimeOrderedEpoch())
					.pinnedForEveryone(false)
					.build();

			pinChatMessageService.pinMessage(document)
			.subscribeOn(DB_SCHEDULER)
			.flatMap(savedDoc -> {		
				// Acknowledge client session
				sendIqResult(id, principal);
				// Disseminate changes downstream
				composeAndSendDirectSync(item.id.trim(), principal, ViewManageEnum.PIN);
				return Mono.empty();
			})
			.doOnError(err -> log.error("Failed handling direct chat pin lifecycle routing execution", err))
			.subscribe();
		}
	}

	public void handlePinChatMessage(String id, String receiverKey, ParsedMessage message, XmppPrincipal principal) {
		String conversationId = DeterministicConversationIdUtil.getConversationId(
				UUID.fromString(principal.getUserKey()), UUID.fromString(receiverKey));

		PinChatMessage document = PinChatMessage.builder()
				.id(new PinChatMessageId(conversationId, UUID.fromString(message.id), UUID.fromString(principal.getUserKey())))
				.seq(UuidCreator.getTimeOrderedEpoch())
				.pinnedForEveryone(true)
				.build();

		pinChatMessageService.pinMessage(document)
		.subscribeOn(DB_SCHEDULER)				
		.doOnError(err -> log.error("Failed handling direct chat pin lifecycle routing execution", err))
		.subscribe();
	}

	public void handlePinGroupMessage(String id, String groupId, ParsedMessage message, XmppPrincipal principal) {
		PinMucMessage document = PinMucMessage.builder()
				.id(new PinMucMessageId(UUID.fromString(groupId), UUID.fromString(id), UUID.fromString(principal.getUserKey())))
				.seq(UuidCreator.getTimeOrderedEpoch())
				.pinnedForEveryone(true)
				.build();

		pinMucMessageService.pinMessage(document)
		.subscribeOn(DB_SCHEDULER)
		.doOnError(err -> log.error("Failed handling group chat pin lifecycle routing execution", err))
		.subscribe();
	}

	/**
	 * Syncs the 'pin' state for 1-on-1 messages to other resources of the user.
	 */
	private void composeAndSendDirectSync(String targetId, XmppPrincipal principal, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();

		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(principal.getBareJid())
				.to(principal.getBareJid()) // To self (Bare JID) triggers cluster user fan-out
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();		
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		// Push to cluster
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.CHAT, false, xml, principal);
	}

	/**
	 * Syncs the 'pin' state for MUC messages and archives the sync event if needed.
	 */
	private void composeAndSendGroupSync(String targetId, String roomJid, XmppPrincipal principal, ViewManageEnum viewManageEnum) {
		String id = UuidCreator.getTimeOrderedEpoch().toString();

		ViewManageSyncStanza vmSync = ViewManageSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.room(roomJid)
				.from(principal.getBareJid())
				.to(roomJid + "/" + principal.getUserKey()) // Target user resource context inside specific room
				.action(viewManageEnum.getValue())
				.build();

		String stanzaId = UuidCreator.getTimeOrderedEpoch().toString();
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), stanzaId, principal.getDomain());

		// Push to cluster
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.GROUPCHAT, false, xml, principal);
	}

	/**
	 * Sends a standard XMPP IQ 'result' stanza back to the user's local session.
	 */
	public void sendIqResult(String id, XmppPrincipal principal) {
		if (id == null || id.isBlank()) {
			log.warn("Attempted to send IQ result with null/empty ID for user {}", principal.getUserKey());
			return;
		}

		String iqResult = String.format("<iq type='result' id='%s'/>", id);
		log.debug("Dispatching IQ result: id={}, user={}", id, principal.getUserKey());

		// Dispatched to the local user session via execution pool thread safety structures
		localStanzaDispatcher.dispatchLocally(
				principal.getUserKey(), 
				principal.getUserKey(), 
				iqResult
				)
		.subscribeOn(DB_SCHEDULER) 
		.doOnError(err -> log.error("Failed to locally dispatch IQ result for user {}", principal.getUserKey(), err))
		.subscribe();
	}
}
package com.algomeet.xmpp.chatservice.routing.dispacher;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.connection.registry.LocalChannelRegistry;
import com.algomeet.xmpp.chatservice.service.XmppSmBufferService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Responsible for delivering XMPP stanzas to locally connected sessions
 * on the current server node.
 *
 * This dispatcher is the final stage in the routing pipeline for:
 * - Local → Local delivery
 * - Remote → Local delivery (after cluster routing)
 *
 * It writes the raw XML stanza into a Netty WebSocket channel
 * associated with the target JID.
 *
 * -------------------------------
 * Key responsibilities
 * -------------------------------
 *
 * 1. Local session resolution via {@link LocalChannelRegistry}
 * 2. Real-time delivery via Netty WebSocket pipeline
 * 3. Stream Management buffering (XEP-0198) for reliability
 * 4. Carbon copy suppression (XEP-0280)
 *
 * -------------------------------
 * Delivery semantics
 * -------------------------------
 *
 * - writeAndFlush() only guarantees the message is accepted by Netty
 * - actual client receipt is not guaranteed at this stage
 * - failures trigger SM buffer fallback when enabled
 *
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalStanzaDispatcher {

	private final LocalChannelRegistry localChannelRegistry;
	private final XmppSmBufferService xmppSmBufferService;

	/**
	 * Dispatches a stanza to a locally connected user session.
	 *
	 * This method handles:
	 * - session lookup
	 * - carbon copy suppression (XEP-0280)
	 * - SM buffer persistence (XEP-0198)
	 * - Netty delivery pipeline execution
	 *
	 * @param to           recipient JID / user key
	 * @param from         sender JID / user key
	 * @param id           stanza identifier for tracking
	 * @param chatType     message type (chat, groupchat, etc.)
	 * @param isCarbonCopy whether this is a carbon copy sync message
	 * @param sessionId    originating session (used to prevent echo loops)
	 * @param payload      raw XML stanza
	 */
	public void dispatchLocally(
			String id,
			String to,
			Boolean isAllowEcho,
			String sessionId,
			String payload) {

		// Resolve recipient's active Netty channel
		Channel targetChannel = localChannelRegistry.getChannel(to);

		
		// Fail fast if user is not currently connected on this node
		if (targetChannel == null) {
			// Persist stanza into SM buffer for reliability (XEP-0198)
			xmppSmBufferService.saveStanzaSynchronized(id, to, payload).subscribe();
			
			log.debug("No active local channel found for JID: {}", to);
			return;
		}

		XmppPrincipal principal =
				targetChannel.attr(XmppSessionAttributes.PRINCIPAL).get();

		// Prevent message loop back to originating session
		if (Boolean.FALSE.equals(isAllowEcho)
				&& principal != null
				&& principal.getSessionId().equals(sessionId)) {

			log.trace("Message suppressed for originating session: {}", sessionId);
			return;
		}

		writeAndFlush(targetChannel, id, to, payload);
	}

	/**
	 * Lightweight local dispatch without SM/carbon-copy context.
	 *
	 * Used for internal or simplified routing paths.
	 */
	public void dispatchLocally(String to, String from, String payload) {

		Channel targetChannel = localChannelRegistry.getChannel(to);

		if (targetChannel == null) {
			log.debug("No active local channel found for JID: {}", to);
			return;
		}

		writeAndFlush(targetChannel, UUID.randomUUID().toString(), to, payload);
	}

	/**
	 * Performs final Netty write operation to the client channel.
	 *
	 * Responsibilities:
	 * - Encapsulate XML stanza into WebSocket frame
	 * - Push message into Netty outbound pipeline
	 * - Handle async success/failure callbacks
	 * - Trigger SM buffer fallback on failure (if enabled)
	 */
	private void writeAndFlush(Channel targetChannel, String id, String to, String payload) {
		targetChannel
		.writeAndFlush(new TextWebSocketFrame(payload))
		.addListener((ChannelFuture future) -> {

			// Successful write means Netty accepted the message for delivery
			if (future.isSuccess()) {
				log.debug("Message delivered. channel={}", targetChannel.id());
				return;
			}

			Channel ch = future.channel();
			log.warn("Delivery failed active={}, open={}, cause={}",
					ch.isActive(),
					ch.isOpen(),
					future.cause().toString());

			// Stream Management fallback
			// Persist stanza into SM buffer for reliability (XEP-0198)
			// If session is resumable, buffer stanza for later replay
			String smSessionId =
					targetChannel.attr(XmppSessionAttributes.SM_ID_KEY).get();

			xmppSmBufferService.saveStanza(id, to, payload, smSessionId)
			.subscribe();
		});
	}
}
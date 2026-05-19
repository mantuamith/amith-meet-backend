package com.algomeet.xmpp.chatservice.routing.dispacher;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.connection.registry.LocalChannelRegistry;
import com.algomeet.xmpp.chatservice.service.XmppSmBufferService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;

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
	public Mono<Boolean> dispatchLocally(
			UUID id,
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
			return Mono.empty();
		}

		XmppPrincipal principal =
				targetChannel.attr(XmppSessionAttributes.PRINCIPAL).get();

		// Prevent message loop back to originating session
		if (Boolean.FALSE.equals(isAllowEcho)
				&& principal != null
				&& principal.getSessionId().equals(sessionId)) {

			log.trace("Message suppressed for originating session: {}", sessionId);
			return Mono.empty();
		}

		return writeAndFlush(targetChannel, id, to, payload);
	}

	/**
	 * Lightweight local dispatch without SM/carbon-copy context.
	 *
	 * Used for internal or simplified routing paths.
	 */
	public Mono<Boolean> dispatchLocally(String to, String from, String payload) {

		Channel targetChannel = localChannelRegistry.getChannel(to);

		if (targetChannel == null) {
			log.debug("No active local channel found for JID: {}", to);
			return Mono.empty();
		}

		return writeAndFlush(targetChannel, UuidCreator.getTimeOrderedEpoch(), to, payload);
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
	private Mono<Boolean> writeAndFlush(Channel targetChannel, UUID id, String to, String payload) {
	    Sinks.One<Boolean> sink = Sinks.one();

	    targetChannel.writeAndFlush(new TextWebSocketFrame(payload))
	    .addListener((ChannelFuture future) -> {
	        if (future.isSuccess()) {
	            log.debug("Message delivered. channel={}", targetChannel.id());
	            // Use FAIL_FAST to handle highly concurrent edge cases gracefully
	            sink.emitValue(true, EmitFailureHandler.FAIL_FAST);
	            return;
	        }
	        
	        // Emit immediately
            sink.emitValue(false, EmitFailureHandler.FAIL_FAST);

	        // Failure Path
	        Channel ch = future.channel();
	        log.warn("Delivery failed active={}, open={}, cause={}",
	                ch.isActive(), ch.isOpen(),
	                future.cause() != null ? future.cause().toString() : "Unknown");

	        try {
	            String smSessionId = targetChannel.attr(XmppSessionAttributes.SM_ID_KEY).get();

	            // CRITICAL FIX: Bind the database fallback to the sink context 
	            // without creating a completely separate unmanaged thread subscription
	            xmppSmBufferService.saveStanza(id, to, payload, smSessionId)
	                    .doOnSuccess(v -> {
	                        log.debug("Fallback storage completed for missed stanza: {}", id);

	                    })
	                    .doOnError(err -> {
	                        log.error("Severe: SM Backup failed for user: {}", to, err);
	                        sink.emitError(err, EmitFailureHandler.FAIL_FAST);
	                    })
	                    .subscribe();
	        } catch (Exception ex) {
	            // Safety net: If reading attributes throws a NullPointerException or similar,
	            // make sure the sink still fires so downstream threads don't hang!
	            log.error("Fatal failure backing-up stanza for stream management resume: {}", id, ex);
	        }
	    });

	    return sink.asMono();
	}
}
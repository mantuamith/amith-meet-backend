package com.algomeet.xmpp.chatservice.routing.dispacher;

import java.util.Collection;
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
import reactor.core.publisher.Flux;
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

	    // 1. Fetch ALL active channels for this user on this node
	    Collection<Channel> targetChannels = localChannelRegistry.getAllChannels(to);

	    // 2. Fallback to Stream Management buffer if the user has no sessions here
	    if (targetChannels == null || targetChannels.isEmpty()) {
	        log.debug("No active local channels found for JID: {}. Routing to SM buffer.", to);
	        return xmppSmBufferService.saveStanzaSynchronized(id, to, payload)
	                .thenReturn(Boolean.FALSE);
	    }

	    // 3. Fan out to all active channels concurrently using Flux
	    return Flux.fromIterable(targetChannels)
	            .flatMap(channel -> {
	                XmppPrincipal principal = channel.attr(XmppSessionAttributes.PRINCIPAL).get();

	                // Evaluate the echo suppression rules individually per session channel
	                if (Boolean.FALSE.equals(isAllowEcho)
	                        && principal != null
	                        && principal.getSessionId().equals(sessionId)) {
	                    
	                    log.trace("Message suppressed for originating session channel: {}", sessionId);
	                    return Mono.just(Boolean.FALSE); // Skip this specific channel, but keep others alive
	                }

	                // Write to this specific socket pipe
	                return writeAndFlush(channel, id, to, payload)
	                        .onErrorReturn(Boolean.FALSE); // Protect the stream if one socket drops out
	            })
	            // 4. Reduce results: Return true if at least one delivery succeeded
	            .reduce(Boolean.FALSE, (accumulator, currentResult) -> accumulator || currentResult);
	}

	/**
	 * Lightweight local dispatch without SM/carbon-copy context.
	 *
	 * Used for internal or simplified routing paths.
	 */
	public Mono<Boolean> dispatchLocally(String to, String from, String payload) {
	    // 1. Fetch ALL active channels for the user on this node
	    Collection<Channel> targetChannels = localChannelRegistry.getAllChannels(to);

	    // 2. Fail fast if the user is completely disconnected from this server instance
	    if (targetChannels == null || targetChannels.isEmpty()) {
	        log.debug("No active local channels found for JID: {}", to);
	        return Mono.just(Boolean.FALSE);
	    }

	    // 3. Generate a distinct transaction ID for this delivery attempt
	    UUID transmissionId = UuidCreator.getTimeOrderedEpoch();

	    // 4. Parallel fan-out across all active sockets
	    return Flux.fromIterable(targetChannels)
	            .flatMap(channel -> writeAndFlush(channel, transmissionId, to, payload)
	                    .onErrorReturn(Boolean.FALSE) // Guard the pipeline if an isolated socket breaks mid-write
	            )
	            // 5. Aggregate metrics: Return true if delivery succeeded on at least one device
	            .reduce(Boolean.FALSE, (accumulator, writeResult) -> accumulator || writeResult);
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
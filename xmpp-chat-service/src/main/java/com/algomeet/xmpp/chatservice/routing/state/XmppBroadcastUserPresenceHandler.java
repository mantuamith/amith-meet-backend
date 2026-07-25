package com.algomeet.xmpp.chatservice.routing.state;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.service.ContactPresenceService;
import com.algomeet.xmpp.chatservice.service.MucPresenceService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * XmppBroadcastUserPresenceHandler orchestrates the propagation of a user's availability state
 * across all MUC (Multi-User Chat) rooms they are currently a member of.
 *
 * This handler includes multi-session arbitration logic to ensure that a user's
 * global presence is only downgraded (e.g., to AWAY) if no other active sessions
 * exist for that user.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class XmppBroadcastUserPresenceHandler {
	private final UserSessionRegistry userSessionRegistry;
	private final MucPresenceService mucPresenceService;
	private final ContactPresenceService contactPresenceService;

	/**
	 * Processes a change in user state and broadcasts the resulting XMPP presence
	 * to all relevant MUC rooms.
	 *
	 * @param ctx       The Netty ChannelHandlerContext for the current connection.
	 * @param principal The security principal representing the authenticated user.
	 * @param newState  The target UserState (ACTIVE, AWAY, DND, etc.).
	 */
	public Mono<Void> broadcastUserPresence(ChannelHandlerContext ctx, XmppPrincipal principal, UserState newState) { 
	    String userKey = principal.getUserKey();

	    // 1. Fetch user sessions asynchronously from Redis
	    return userSessionRegistry.getSessions(userKey)
	        .flatMap(sessions -> {
	            // Safe fallback if the collection is somehow null or empty
	            if (sessions == null || sessions.isEmpty()) {
	                return Mono.just(true); // Allow broadcasting to continue
	            }

	            // 1. Multi-Session Arbitration: Prevent "Presence Flickering"
	            // If the user is GONE on this session, but has other live sessions, do NOT broadcast 'unavailable' yet.
	            if (newState == UserState.GONE && sessions.stream().anyMatch(s -> s.getState() != UserState.GONE)) {
	                log.debug("User {} is GONE on one session but remains online elsewhere.", userKey);
	                return Mono.just(false); // Suppress broadcast
	            }

	            // 2. If the update is an idle state (AWAY/INACTIVE), ignore it if any other session is currently ACTIVE.
	            if (newState == UserState.AWAY || newState == UserState.INACTIVE) {
	                if (sessions.stream().anyMatch(s -> s.getState() == UserState.ACTIVE)) {
	                    log.debug("Ignoring {} update; user {} is still ACTIVE on another device.", newState, userKey);
	                    return Mono.just(false); // Suppress broadcast
	                }
	            }

	            // Note: DND is NOT suppressed here. If newState == DND, we allow it to propagate.
	            return Mono.just(true); // Allow broadcasting to continue
	        })
	        .flatMap(shouldBroadcast -> {
	            // If arbitration determined we should skip publishing, stop here cleanly
	            if (!shouldBroadcast) {
	                return Mono.empty();
	            }

	            // 2. Execute presence distributions concurrently using Mono.when
	            return Mono.when(
	                // Publish presence to contacts
	                contactPresenceService.broadcastPresenceToContacts(ctx, principal, newState),
	                
	                // Publish presence to groups
	                mucPresenceService.broadcastPresenceToAllJoinedGroups(ctx, userKey, newState)
	            );
	        })
	        .doOnError(e -> log.error("Failed to broadcast presence for user {}: {}", userKey, e.getMessage()));
	}
}
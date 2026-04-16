package com.algomeet.xmpp.chatservice.routing.state;

import java.util.Set;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.service.ContactPresenceService;
import com.algomeet.xmpp.chatservice.service.MucPresenceService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
	@Async("presenceExecutor")
	public void broadUserPresenceAsync(ChannelHandlerContext ctx, XmppPrincipal principal, UserState newState) { 
		// 1. Multi-Session Arbitration:
		// We check if the user has other active connections to prevent "Presence Flickering".
		Set<UserSession> sessions = userSessionRegistry.getSessions(principal.getUserKey());

		if (sessions != null) {
			// --- REFINED ARBITRATION LOGIC ---

			// 1. If the user is GONE on this session, but has other live sessions, 
			// do NOT broadcast 'unavailable' yet.
			if (newState == UserState.GONE && sessions.stream().anyMatch(s -> s.getState() != UserState.GONE)) {
				log.debug("User {} is GONE on one session but remains online elsewhere.", principal.getUserKey());
				return; 
			}

			// 2. If the update is an idle state (AWAY/INACTIVE), ignore it if 
			// any other session is currently ACTIVE.
			if (newState == UserState.AWAY || newState == UserState.INACTIVE) {
				if (sessions.stream().anyMatch(s -> s.getState() == UserState.ACTIVE)) {
					log.debug("Ignoring {} update; user {} is still ACTIVE on another device.", newState, principal.getUserKey());
					return; 
				}
			}

			// Note: DND is NOT suppressed here. If newState == DND, we allow it 
			// to propagate because it is a deliberate "Do Not Disturb" intent.
		}
		
		// Publish presence to groups
		contactPresenceService.broadcastPresenceToContacts(ctx, principal, newState);

		// Publish presence to contacts
		mucPresenceService.broadcastPresenceToParticipants(ctx, principal, newState);
	}	
}
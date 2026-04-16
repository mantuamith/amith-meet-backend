package com.algomeet.xmpp.chatservice.routing.state;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.parser.StateStanzaParser;
import com.algomeet.xmpp.chatservice.routing.chat.OfflineMessageHandler;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Manages the lifecycle and availability states of an XMPP session.</p>
 * 
 * <p>This handler is responsible for interpreting XMPP signals that affect a user's 
 * reachability and session status. It processes two primary types of updates:</p>
 * <ul>
 *     <li><b>Presence (XEP-0186):</b> Updates user availability (e.g., Available, DND, Away) 
 *         and triggers the initial "sync" of offline messages.</li>
 *     <li><b>Chat States (XEP-0085):</b> Monitors activity indicators (e.g., Active, 
 *         Inactive, Gone) to optimize session resource management.</li>
 * </ul>
 * 
 * <p><b>Key Lifecycle Logic:</b> When a user first sends an 'available' presence, 
 * this component delegates to {@link OfflineMessageHandler} to flush any messages 
 * that were stored while the user was disconnected.</p>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppUserGlobalPresenceHandler {

	private final UserSessionRegistry userSessionRegistry;
	private final OfflineMessageHandler offlineMessageHandler;
	private final XmppBroadcastUserPresenceHandler xmppUserGlobalPresenceHandler;
	private final XmppPresencePushHandler xmppPresencePushHandler;

	/**
	 * Processes incoming XML for status-related updates and lifecycle triggers.
	 * It synchronizes the user's availability and activates the session by 
	 * flushing the offline message queue upon the first valid presence signal.
	 * 
	 * @param ctx       The Netty {@link ChannelHandlerContext}.
	 * @param principal The authenticated user identity.
	 * @param xml       The raw XML stanza content.
	 */
	public void processPresence(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
		if (isSelfBroadcastPresence(xml) ) {
			// Extract human-readable status from the XML before processing
			UserState newState = determineState(xml);
			if (newState == null) return;

			// 1. Sync the global session registry
			userSessionRegistry.updateSessionStatus(principal.getUserKey(), principal.getSessionId(), newState);

			// 2. Broadcast the user's presence update to all contacts and joined groups
			xmppUserGlobalPresenceHandler.broadUserPresenceAsync(ctx, principal, newState);

			Attribute<Boolean> initialPresenceAttr = ctx.channel().attr(XmppSessionAttributes.IS_INITIAL_PRESENCE_SENT);			
			if(initialPresenceAttr == null || !initialPresenceAttr.get()) {
				// Push contacts and groups presence to user
				xmppPresencePushHandler.pushUsersPresenceAsync(ctx, principal);

				offlineMessageHandler.deliverOfflineMessages(ctx, principal);
				// Initial user presence to true
				initialPresenceAttr.set(true);

				log.info("Session activation for {}. Delivering missed content.", principal.getUserKey());    
			}
		}
	}
	
	/**
	 * Detects if a presence stanza is a self-broadcast request.
	 * * According to RFC 6121, a presence stanza with no 'to' attribute 
	 * is a signal to the server to broadcast the user's availability 
	 * to all subscribed entities (the roster) and joined MUCs.
	 *
	 * @param xml The raw inbound XMPP string.
	 * @return true if it is a <presence/> and lacks a 'to' attribute.
	 */
	public static boolean isSelfBroadcastPresence(String xml) {
		if (xml == null) return false;

		// 1. Efficiently locate the first tag
		int firstTag = xml.indexOf('<');
		if (firstTag == -1) return false;

		// 2. Verify it is a <presence/> stanza
		// We check for "<presence" to account for trailing spaces or attributes
		if (xml.regionMatches(true, firstTag, "<presence", 0, 9)) {

			// 3. Check for the 'to' attribute
			// A self-broadcast MUST NOT have a 'to' destination.
			// We look for " to=" or " to='".
			return !xml.contains(" to=") && !xml.contains(" to='");
		}

		return false;
	}
	

	private UserState determineState(String xml) {
		// Guard clause: ignore stanzas that are not Presence or Chat State notifications
		try {

			if (XmppStanzaUtil.isPresenceStanza(xml)) {
				return StateStanzaParser.determineState(xml);
			}
		} catch(Exception ex) {
			// Silent error
		}

		return null;
	}
}
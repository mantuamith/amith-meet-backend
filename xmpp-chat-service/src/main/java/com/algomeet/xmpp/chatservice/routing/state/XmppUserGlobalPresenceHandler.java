package com.algomeet.xmpp.chatservice.routing.state;

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
 * <p>Maintains global reachability and coordinates session activation.</p>
 * * <p>This handler acts as the <b>Presence Orchestrator</b>. When a user sends their 
 * initial availability, this component triggers a multi-stage activation process:</p>
 * <ul>
 * <li><b>Outbound:</b> Broadcasts the user's status to their roster and groups.</li>
 * <li><b>Inbound:</b> Pushes the current presence of all contacts back to the user.</li>
 * <li><b>Persistence:</b> Flushes stored offline messages to the active stream.</li>
 * </ul>
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
	 * Processes inbound presence stanzas to update session state and trigger syncs.
	 * * @param ctx       The Netty context for the current socket.
	 * @param principal The authenticated user identity.
	 * @param xml       The raw XMPP presence stanza.
	 */
	public void processPresence(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
		// RFC 6121: Only process presence intended for the server (self-broadcast)
		if (isSelfBroadcastPresence(xml)) {
			
			// Parse the XML to determine the specific availability (e.g., AWAY, DND, CHAT)
			UserState newState = determineState(xml);
			if (newState == null) return;

			// 1. Sync the Global Registry: Allows other nodes in the cluster to see this user is online
			userSessionRegistry.updateSessionStatus(principal.getUserKey(), principal.getSessionId(), newState);

			// 2. Outbound Broadcast: Notify the world that this user is now reachable
			xmppUserGlobalPresenceHandler.broadUserPresenceAsync(ctx, principal, newState);

			// 3. Initial Session Activation Logic
			// We use a Channel Attribute to ensure "Initial Sync" logic only runs once per session.
			Attribute<Boolean> initialPresenceAttr = ctx.channel().attr(XmppSessionAttributes.IS_INITIAL_PRESENCE_SENT);			
			
			if (initialPresenceAttr.get() == null || !initialPresenceAttr.get()) {
				
				// A. Push "World State": Let the user know who else is online (Contacts/Groups)
				xmppPresencePushHandler.pushUsersPresenceAsync(ctx, principal);

				// B. Deliver Missed Content: Push messages stored while the user was offline
				offlineMessageHandler.deliverOfflineMessages(ctx, principal);

				// Mark session as "Active" to prevent redundant syncs on subsequent status changes
				initialPresenceAttr.set(true);

				log.info("Session activation complete for {}. Syncing presence and offline history.", principal.getUserKey());    
			}
		}
	}
	
	/**
	 * Detects if a presence stanza is a self-broadcast request.
	 * <p>According to RFC 6121, a presence stanza with no 'to' attribute 
	 * is a signal to the server to broadcast the user's availability 
	 * to all subscribed entities (the roster) and joined MUCs.</p>
	 */
	public static boolean isSelfBroadcastPresence(String xml) {
		if (xml == null) return false;

		int firstTag = xml.indexOf('<');
		if (firstTag == -1) return false;

		// Check if the root element is <presence
		if (xml.regionMatches(true, firstTag, "<presence", 0, 9)) {
			// A self-broadcast MUST NOT have a 'to' destination.
			// Presence with a 'to' attribute is a directed presence (XEP-0045) or subscription request.
			return !xml.contains(" to=") && !xml.contains(" to='");
		}

		return false;
	}
	
	/**
	 * Extracts the UserState enum from the raw XML stanza.
	 */
	private UserState determineState(String xml) {
		try {
			if (XmppStanzaUtil.isPresenceStanza(xml)) {
				return StateStanzaParser.determineState(xml);
			}
		} catch(Exception ex) {
			log.warn("Failed to parse presence state for stanza: {}", xml);
		}
		return null;
	}
}
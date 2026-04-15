package com.algomeet.xmpp.chatservice.routing.state;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.ContactClient;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucMemberOrdinaryPresenceEventHandler;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.util.JidUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * XmppUserPresenceHandler orchestrates the propagation of a user's availability state
 * across all MUC (Multi-User Chat) rooms they are currently a member of.
 *
 * This handler includes multi-session arbitration logic to ensure that a user's
 * global presence is only downgraded (e.g., to AWAY) if no other active sessions
 * exist for that user.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class XmppUserPresenceHandler {
	private final GroupClient groupClient;
	private final JidUtil jidUtil;
	private final MucMemberOrdinaryPresenceEventHandler mucMemberPresenceEventHandler;
	private final UserSessionRegistry userSessionRegistry;
	private final ContactClient contactClient;
	private final ClusterMessagePublisher clusterMessagePublisher;

	/**
	 * Processes a change in user state and broadcasts the resulting XMPP presence
	 * to all relevant MUC rooms.
	 *
	 * @param ctx       The Netty ChannelHandlerContext for the current connection.
	 * @param principal The security principal representing the authenticated user.
	 * @param newState  The target UserState (ACTIVE, AWAY, DND, etc.).
	 */
	public void handleUserPresence(ChannelHandlerContext ctx, XmppPrincipal principal, UserState newState, String xml) {    
		if (!isSelfBroadcastPresence(xml)) {
			// Return if not self broadcast presence
			return;
		}

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

		try {
			// 2. Fetch all MUC rooms where this user is a participant.
			List<MucRoomDto> groups = groupClient.getGroupsForUserKey(principal.getUserKey());

			if (!CollectionUtils.isEmpty(groups)) {
				for (MucRoomDto group : groups) {
					// Generate the room-specific JID (e.g., room_id@conference.algomeet.app)
					String roomJid = jidUtil.getGroupBareJid(group.getId());

					// Locate the user's specific membership metadata within the group context
					Optional<MucMember> senderMucMember = group.getMembers().stream()
							.filter(m -> m.getUserKey().equals(principal.getUserKey()))
							.findFirst();

					if (senderMucMember.isPresent()) {
						// Trigger the internal event handler to broadcast presence to other room occupants
						mucMemberPresenceEventHandler.handleMemberPresence(
								ctx, 
								roomJid, 
								principal.getBareJid(), 
								null, 
								group, 
								senderMucMember.get(),
								newState
								);
					}
				}
			}
		} catch (Exception ex) {
			log.error("Error retrieving groups for presence update using user key: {}", principal.getUserKey(), ex);
		}

		try {
			List<UUID> acceptedContacts = contactClient.getAcceptedContacts(UUID.fromString(principal.getUserKey()));

			if (!CollectionUtils.isEmpty(acceptedContacts)) {
				for (UUID contactUserKey : acceptedContacts) {
					// Generate the compliant XMPP <presence/> XML string
					String presenceXml = buildDirectPresence(newState, principal.getBareJid(), jidUtil.getBareJid(contactUserKey.toString()));

					clusterMessagePublisher.convertAndSendToUser(
							UUID.randomUUID().toString(), 
							contactUserKey.toString(), 
							principal.getUserKey(), 
							ChatType.CHAT, 
							presenceXml
							);
				}
			}

		} catch (Exception ex) {
			log.error("Error retrieving contacts for presence update using user key: {}", principal.getUserKey(), ex);
		}
	}
	
	/**
	 * Builds presence for 1:1 direct chat (standard XMPP).
	 */
	public String buildDirectPresence(UserState state, String from, String to) {
		return wrapInPresenceStanza(state, from, to, getPresenceStatusElements(state));
	}

	/**
	 * Builds a self-broadcast presence (no 'to' or 'from').
	 */
	public String buildSelfPresence(UserState state, String id) {
		String typeAttr = (state == UserState.GONE) ? " type='unavailable'" : "";
		return String.format("<presence id='%s'%s>%s</presence>", 
				id, typeAttr, getPresenceStatusElements(state));
	}

	/**
	 * Internal helper to map UserState to XMPP <show> elements.
	 */
	private String getPresenceStatusElements(UserState state) {
		return switch (state) {
		case AWAY -> "<show>away</show>";
		case INACTIVE -> "<show>xa</show>";
		case DND -> "<show>dnd</show>";
		default -> "";
		};
	}

	/**
	 * Standardizes the wrapping of presence tags.
	 */
	private String wrapInPresenceStanza(UserState state, String from, String to, String internalXml) {
		String typeAttr = (state == UserState.GONE) ? " type='unavailable'" : "";
		return String.format("<presence from='%s' to='%s'%s>%s</presence>",
				from, to, typeAttr, internalXml);
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
}
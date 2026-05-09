package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.PresenceMetaAction;
import com.algomeet.xmpp.chatservice.enums.PresenceType;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucAcceptInviteEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucChangeNickNameEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucMemberJoinEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucMemberLeftEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucMemberPresenceEventHandler;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.util.MucMetaActionParser;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the dispatching and broadcasting of MUC-related user commands.
 * <p>
 * This component handles real-time state changes within a Multi-User Chat room, 
 * such as nickname changes, by generating the appropriate XMPP stanzas and 
 * publishing them to the cluster for distributed delivery.
 * </p>
 * * @author Algomeet Core Team
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucUserCommandRouter {    
	private final GroupCacheService groupCacheService;
	private final MucAcceptInviteEventHandler mucAcceptInviteEventHandler;
	private final MucChangeNickNameEventHandler mucChangeNickNameEventHandler;
	private final MucMemberJoinEventHandler mucMemberJoinEventHandler;
	private final MucMemberPresenceEventHandler mucMemberPresenceEventHandler;
	private final MucMemberLeftEventHandler mucMemberLeftEventHandler;
	/**
	 * Top-level handler for incoming command stanzas targeting a specific room.
	 * * @param ctx       The Netty channel context for the current session.
	 * @param roomJid   The full JID of the room, often including the requested nick.
	 * @param senderJid The real JID of the user initiating the command.
	 * @param xml       The raw XML payload.
	 * @param group     The room DTO containing current occupant information.
	 * @param sender    The MUC member profile of the initiator.
	 */
	public void handleCommandStanza(ChannelHandlerContext ctx, String type, String roomJid, String xml, XmppPrincipal principal) {
		// Set tenant Id to support multi-tenancy 
		TenantContext.setCurrentTenant(principal.getTenantId());
		
		Optional<String> actionOpt = MucMetaActionParser.extractAction(xml);
		String action = actionOpt.orElse(null);
		
		// Force refresh group cache
		MucRoomDto group = groupCacheService.refreshCachedGroup(XmppUtil.getRoomId(roomJid));
		Optional<MucMember> senderMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(principal.getUserKey()))
				.findFirst();

		if (PresenceMetaAction.INVITE_ACCEPT == PresenceMetaAction.fromString(action)) {
			/**
			 * Accepted invite request stanza.
			 *
			 * Example:
			 * <presence to='room@conference.example.com/nick'>
			 *   <x xmlns='http://jabber.org/protocol/muc'/>
			 *   <x xmlns='http://algomeet.app/protocol/muc#meta'>
			 *     <action>invite_accept</action>
			 *   </x>
			 * </presence>
			 */
			mucAcceptInviteEventHandler.handleAcceptedInvite(ctx,  roomJid, xml, group, senderMucMember.get());
			
		} else {
			String[] roomJidArr = roomJid.split("/");
			String resoure = null;

			if(roomJidArr.length > 1 && StringUtils.hasText(roomJidArr[1])) {
				resoure = roomJidArr[1].trim();
			}

			if (isPublishPresenceRequest(xml)) {
				/**
				 * Join room request stanza.
				 *
				 * Example:
				 * <presence to='room@conference.example.com/nick'>
				 *   <x xmlns='http://jabber.org/protocol/muc'/>
				 * </presence>
				 */
				mucMemberJoinEventHandler.handleMemberJoinRequest(ctx, roomJid, xml, group, senderMucMember.get());	
				
			} else if (PresenceType.UNAVAILABLE.getValue().equals(type)) {
				/**
				 * Left room request stanza.
				 *
				 * Example:
				 * <presence
				 *   to='room@conference.example.com/nick'
				 *   type='unavailable'/>
				 */
				mucMemberLeftEventHandler.handleMemberLeftRoom(ctx, roomJid, xml, group, principal);
				
			} else if (resoure != null && resoure.trim().equalsIgnoreCase(senderMucMember.get().getUserKey())) {
				/**
				 * Member room presence update request stanza.
				 *
				 * Example:
				 * <presence to='room@conference.example.com/nick'>
				 *   <show>away</show>
				 *   <status>AFK</status>
				 * </presence>
				 */
				mucMemberPresenceEventHandler.handleMemberPresenceRequest(ctx, roomJid, xml, group, senderMucMember.get());
			
			} else {
				/**
				 * Change group member nickname request stanza.
				 *
				 * Example:
				 * <presence 
				 *     to='289c5f4d-58a0-4def-bf5b-0fd15c045575@conference.algomeet.app/James'/>
				 */
				mucChangeNickNameEventHandler.handleChangeNicknameRequest(ctx, roomJid, xml, group, senderMucMember.get());
			}
		}
	}

	/**
	 * Checks if the stanza is a MUC presence publish/join request.
	 *
	 * This is used to detect when a user is joining or publishing presence to a room,
	 * identified by the presence of the MUC namespace.
	 *
	 * Example detected pattern:
	 * <presence>
	 *   <x xmlns='http://jabber.org/protocol/muc'/>
	 * </presence>
	 */
	private boolean isPublishPresenceRequest(String xml) {
	    return (xml.contains("http://jabber.org/protocol/muc"));
	}
}
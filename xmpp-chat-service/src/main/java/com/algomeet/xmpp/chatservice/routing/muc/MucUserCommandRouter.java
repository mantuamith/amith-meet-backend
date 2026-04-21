package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.PresenceMetaAction;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucAcceptInviteEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucChangeNickNameEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucMemberJoinEventHandler;
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
	/**
	 * Top-level handler for incoming command stanzas targeting a specific room.
	 * * @param ctx       The Netty channel context for the current session.
	 * @param roomJid   The full JID of the room, often including the requested nick.
	 * @param senderJid The real JID of the user initiating the command.
	 * @param xml       The raw XML payload.
	 * @param group     The room DTO containing current occupant information.
	 * @param sender    The MUC member profile of the initiator.
	 */
	public void handleCommandStanza(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, XmppPrincipal principal) {
		// Set tenant Id to support multi-tenancy 
		TenantContext.setCurrentTenant(principal.getTenantId());

		// Force refresh group cache
		MucRoomDto group = groupCacheService.refreshCachedGroup(XmppUtil.getRoomId(roomJid));
		Optional<MucMember> senderMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(principal.getUserKey()))
				.findFirst();

		Optional<String> actionOpt = MucMetaActionParser.extractAction(xml);
		String action = actionOpt.orElse(null);

		if (PresenceMetaAction.INVITE_ACCEPT == PresenceMetaAction.fromString(action)) {
			mucAcceptInviteEventHandler.handleAcceptedInvite(ctx,  roomJid, xml, group, senderMucMember.get());
		} else {

			String[] roomJidArr = roomJid.split("/");
			String resoure = null;
			if(roomJidArr.length > 1 && StringUtils.hasText(roomJidArr[1])) {
				resoure = roomJidArr[1].trim();
			}

			if (isPublishPresenceRequest(xml)) {
				mucMemberJoinEventHandler.handleMemberJoin(ctx,  roomJid, xml, group, senderMucMember.get());

			} else if (resoure != null && resoure.trim().equalsIgnoreCase(senderMucMember.get().getUserKey())) {
				mucMemberPresenceEventHandler.handleMemberPresence(ctx, roomJid, xml, group, senderMucMember.get());

			} else {
				
				mucChangeNickNameEventHandler.handleChangeNicknameRequest(ctx, roomJid, xml, group, senderMucMember.get());
			}
		}
	}

	private boolean isPublishPresenceRequest(String xml) {
		return (xml.contains("http://jabber.org/protocol/muc"));
	}     

}
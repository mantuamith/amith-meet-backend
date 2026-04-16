package com.algomeet.xmpp.chatservice.routing.muc;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucAddMemberEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucKickEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucMuteEventHandler;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucUnMuteEventHandler;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.util.MucCommandUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates administrative commands for Multi-User Chat (MUC) rooms.
 * <p>
 * This component handles high-privilege operations within the {@code http://jabber.org/protocol/muc#admin} 
 * namespace. It manages the business logic for occupant removal (kicking), voice management (muting/unmuting), 
 * and affiliation updates (member promotion).
 * </p>
 * <p>
 * <b>Architecture Role:</b> It validates moderator permissions, generates protocol-compliant presence broadcasts, 
 * and uses the {@link ClusterMessagePublisher} to ensure state changes are synchronized across all 
 * active cluster nodes.
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.1
 */
@Slf4j
@Component 
@RequiredArgsConstructor
public class MucAdminCommandRouter {
	private final GroupCacheService groupCacheService;
	private final MucKickEventHandler mucKickEventHandler;
	private final MucMuteEventHandler mucMuteEventHandler;
	private final MucUnMuteEventHandler mucUnMuteEventHandler;
	private final MucAddMemberEventHandler mucAddMemberEventHandler;
	/**
	 * Routes an incoming XML command stanza to the appropriate internal handler.
	 *
	 * @param ctx       The Netty channel context for the current session.
	 * @param roomJid   The JID of the target MUC room.
	 * @param senderJid The real JID of the user initiating the command.
	 * @param xml       The raw XML payload of the IQ stanza.
	 * @param group     The data transfer object representing the current room state.
	 * @param sender    The {@link MucMember} profile of the initiator for permission validation.
	 */
	public void handleCommandStanza(ChannelHandlerContext ctx, String roomJid, String xml, MucMember sender) {
		// Force refresh group cache
		MucRoomDto group = groupCacheService.getCachedGroup(XmppUtil.getRoomId(roomJid), true);
				
		if (MucCommandUtil.isKickPayload(xml)) {
			mucKickEventHandler.handleKickRequest(ctx, roomJid, xml, group, sender);
		} else if (MucCommandUtil.isMutePayload(xml)) {
			mucMuteEventHandler.handleMuteRequest(ctx, roomJid, xml, group, sender);
		} else if (MucCommandUtil.isUnMutePayload(xml)) {
			mucUnMuteEventHandler.handleUnMuteRequest(ctx, roomJid, xml, group, sender);
		} else if (MucCommandUtil.isAddMemberStanza(xml)) {
			mucAddMemberEventHandler.handleAddMember(ctx, roomJid, xml, group, sender);
		}
	}	
}
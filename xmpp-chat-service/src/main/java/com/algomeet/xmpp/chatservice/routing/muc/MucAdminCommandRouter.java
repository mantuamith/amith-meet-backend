package com.algomeet.xmpp.chatservice.routing.muc;

import org.springframework.stereotype.Component;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
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
	public void handleCommandStanza(ChannelHandlerContext ctx, String roomJid, String xml, MucMember sender, XmppPrincipal principal) {
    	// Set tenant Id to support multi-tenancy 
    	TenantContext.setCurrentTenant(principal.getTenantId());
    	
		// Force refresh group cache
		MucRoomDto group = groupCacheService.refreshGroupCache(XmppUtil.getRoomId(roomJid));
				
		if (MucCommandUtil.isKickPayload(xml)) {
			/**
			 * Kick or remove member request stanza.
			 *
			 * Example:
			 * <iq from='2fc35cae-e0b7-40a5-b2aa-e86206730e99@algomeet.app'
			 *     id='kick-request-11121'
			 *     to='289c5f4d-58a0-4def-bf5b-0fd15c045575@conference.algomeet.app'
			 *     type='set'>
			 *   <query xmlns='http://jabber.org/protocol/muc#admin'>
			 *     <item jid='50748cb4-940e-4a97-b4f2-86125d207a1c@algomeet.app' role='none'>
			 *       <reason>Please stay on topic.</reason>
			 *     </item>
			 *   </query>
			 * </iq>
			 */
			mucKickEventHandler.handleKickMemberRequest(ctx, roomJid, xml, group, sender);
		} else if (MucCommandUtil.isMutePayload(xml)) {
			/**
			 * Mute member request stanza.
			 *
			 * Example:
			 * <iq from='2fc35cae-e0b7-40a5-b2aa-e86206730e99@algomeet.app'
			 *     id='kick-request-11120'
			 *     to='289c5f4d-58a0-4def-bf5b-0fd15c045575@conference.algomeet.app'
			 *     type='set'>
			 *   <query xmlns='http://jabber.org/protocol/muc#admin'>
			 *     <item jid='xxxx-xxxx@algomeet.app' role='visitor'>
			 *       <reason>Please stay on mute during the demo.</reason>
			 *     </item>
			 *   </query>
			 * </iq>
			 */
			mucMuteEventHandler.handleMuteRequest(ctx, roomJid, xml, group, sender);
		} else if (MucCommandUtil.isUnMutePayload(xml)) {
			/**
			 * Unmute member request stanza.
			 *
			 * Example:
			 * <iq from='2fc35cae-e0b7-40a5-b2aa-e86206730e99@algomeet.app'
			 *     id='unmute_01'
			 *     to='289c5f4d-58a0-4def-bf5b-0fd15c045575@conference.algomeet.app'
			 *     type='set'>
			 *   <query xmlns='http://jabber.org/protocol/muc#admin'>
			 *     <item jid='xxxx-xxxx@algomeet.app' role='participant'>
			 *       <reason>Issue resolved, restoring voice.</reason>
			 *     </item>
			 *   </query>
			 * </iq>
			 */
			mucUnMuteEventHandler.handleUnMuteRequest(ctx, roomJid, xml, group, sender);
		} else if (MucCommandUtil.isAddMemberStanza(xml)) {
			/**
			 * Add member request stanza.
			 *
			 * Example:
			 * <iq from='2fc35cae-e0b7-40a5-b2aa-e86206730e99@algomeet.app'
			 *     id='add_user_01'
			 *     to='289c5f4d-58a0-4def-bf5b-0fd15c045575@conference.algomeet.app'
			 *     type='set'>
			 *   <query xmlns='http://jabber.org/protocol/muc#admin'>
			 *     <item affiliation='member' jid='xxxx-xxxx@algomeet.app'>
			 *       <reason>Onboarding to the Backend Team</reason>
			 *     </item>
			 *   </query>
			 * </iq>
			 */
			mucAddMemberEventHandler.handleAddMemberRequest(ctx, roomJid, xml, group, sender);
		}
	}	
}
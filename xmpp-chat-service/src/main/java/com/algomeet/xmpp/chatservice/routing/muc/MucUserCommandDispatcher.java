package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
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
public class MucUserCommandDispatcher {
    
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final GroupCacheService groupCacheService;

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
    	// Force refresh group cache
    	MucRoomDto group = groupCacheService.getCachedGroup(Long.parseLong(XmppUtil.getRoomId(roomJid)), true);
    	Optional<MucMember> senderMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(principal.getUserKey()))
				.findFirst();
    			
        handleChangeNicknameRequest(ctx, roomJid, senderJid, xml, group, senderMucMember.get());
    }

    /**
     * Processes a nickname change request (XEP-0045).
     * <p>
     * The process follows the "unavailable-then-available" sequence:
     * <ol>
     * <li>Extracts the new nickname from the room JID.</li>
     * <li>Broadcasts an "unavailable" presence with status code 303 (New Nickname).</li>
     * <li>Broadcasts a new "available" presence under the new nickname.</li>
     * </ol>
     * </p>
     * * @param ctx       The Netty channel context.
     * @param roomJid   Requested JID (e.g., room@conference.domain/NewNick).
     * @param senderJid Initiator's real JID.
     * @param xml       The presence stanza.
     * @param group     Current room state and member list.
     * @param sender    Sender's current member metadata.
     */
    public void handleChangeNicknameRequest(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {
        // 1. Extract metadata (nickname)
        String[] jidArr = roomJid.split("/");
        String newNickname = null;
        if(jidArr.length > 1 && StringUtils.hasText(jidArr[1])) {
            newNickname = jidArr[1].trim();
        }

        String mucAffiliation = MucAffiliation.fromString(sender.getRole()).getValue();
        
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        log.info("User {} attempting to change nickname {} from room {}", senderJid, newNickname, roomJid);

        // 2. Construct the rename presence 
        String renamePresence = buildRenamePresence(roomBareJid, sender.getNickname(), newNickname, mucAffiliation,
                MucRoleUtil.getMucRole(sender.getRole()).getValue());

        // 3. Broadcast "Old Nick" exit to the Room
        for(MucMember receiverMucMember : group.getMembers()) {
            String toUserKey = receiverMucMember.getUserKey();
            clusterMessagePublisher.convertAndSendToUser(UUID.randomUUID().toString(), toUserKey, sender.getUserKey(), ChatType.GROUPCHAT, renamePresence);
        }
        
        // 4. Construct the available presence 
        String availablePresence = buildAvailablePresence(roomBareJid, newNickname, mucAffiliation, 
                MucRoleUtil.getMucRole(sender.getRole()).getValue()); 

        // 5. Broadcast "New Nick" entry to the Room
        for(MucMember receiverMucMember : group.getMembers()) {
            String toUserKey = receiverMucMember.getUserKey();
            clusterMessagePublisher.convertAndSendToUser(UUID.randomUUID().toString(), toUserKey, sender.getUserKey(), ChatType.GROUPCHAT, availablePresence);
        }

        log.info("User successful: {} changed nickname from {}", newNickname, senderJid);
    }

    /**
     * Builds the XMPP presence stanza indicating a nickname change via status code 303.
     */
    private String buildRenamePresence(String roomJid, String oldNick, String newNick, String affiliation, String role) {
        return String.format(
                "<presence from='%s/%s' type='unavailable'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' nick='%s'/>" +
                "    <status code='303'/>" +
                "  </x>" +
                "</presence>",
                roomJid, oldNick, affiliation, role, newNick
        );
    }
    
    /**
     * Builds the standard XMPP available presence for a room occupant.
     */
    private String buildAvailablePresence(String roomJid, String nick, String affiliation, String role) {
        return String.format(
                "<presence from='%s/%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s'/>" +
                "  </x>" +
                "</presence>",
                roomJid, nick, affiliation, role
        );
    }    
}
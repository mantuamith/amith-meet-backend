package com.algomeet.xmpp.chatservice.routing.muc.events;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MucChangeNickNameEventHandler {
    private final MucMessageRouter mucMessageRouter;
    private final JidUtil jidUtil;

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
     * @param xml       The presence stanza.
     * @param group     Current room state and member list.
     * @param sender    Sender's current member metadata.
     */
    public void handleChangeNicknameRequest(ChannelHandlerContext ctx, String roomJid, String xml, Group group, GroupMember sender) {
        // 1. Extract metadata (nickname)
        String[] jidArr = roomJid.split("/");
        String newNickname = null;
        if(jidArr.length > 1 && StringUtils.hasText(jidArr[1])) {
            newNickname = jidArr[1].trim();
        }

        String mucAffiliation = MucAffiliation.fromString(sender.getRole()).getValue();
        String senderJid = jidUtil.getBareJid(sender.getUserKey());
        
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        log.info("User {} attempting to change nickname {} from room {}", senderJid, newNickname, roomJid);

        // 2. Construct the rename presence 
        String renamePresence = buildRenamePresence(roomBareJid, sender.getUserKey(), newNickname, mucAffiliation,
                MucRoleUtil.getMucRole(sender.getRole()).getValue());

        // 3. Broadcast "Old Nick" exit to the Room
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();   
        mucMessageRouter.broadcastToOccupants(UuidCreator.getTimeOrderedEpoch().toString(), sender.getUserKey(), group, renamePresence, principal.getSessionId());
        
        // 4. Construct the available presence 
        String availablePresence = buildAvailablePresence(roomBareJid, sender.getUserKey(), mucAffiliation, 
                MucRoleUtil.getMucRole(sender.getRole()).getValue()); 

        // 5. Broadcast "New Nick" entry to the Room
        mucMessageRouter.broadcastToOccupants(UuidCreator.getTimeOrderedEpoch().toString(), sender.getUserKey(), group, availablePresence, true);

        log.info("User successful: Changed nickname to {}", newNickname);
    }

    /**
     * Builds the XMPP presence stanza indicating a nickname change via status code 303.
     */
    private String buildRenamePresence(String roomJid, String userKey, String newNick, String affiliation, String role) {
        return String.format(
                "<presence from='%s/%s' type='unavailable'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' nick='%s'/>" +
                "    <status code='303'/>" +
                "  </x>" +
                "</presence>",
                roomJid, userKey, affiliation, role, newNick
        );
    }
    
    /**
     * Builds the standard XMPP available presence for a room occupant.
     */
    private String buildAvailablePresence(String roomJid, String userKey, String affiliation, String role) {
        return String.format(
                "<presence from='%s/%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s'/>" +
                "  </x>" +
                "</presence>",
                roomJid, userKey, affiliation, role
        );
    }    
}

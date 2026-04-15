package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the broadcast of presence updates when a member interacts with a MUC room.
 * This handler ensures that both the joining user and existing occupants are synchronized 
 * with the current room state according to XEP-0045 standards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucMemberPresenceEventHandler {

    private final ClusterMessagePublisher clusterMessagePublisher;
    private final JidUtil jidUtil;

    /**
     * Handles the successful entry of a member into a room by broadcasting presence.
     * * @param ctx       The Netty channel context for the current session.
     * @param roomJid   The full JID of the room.
     * @param senderJid The real JID of the user who is sending the presence.
     * @param xml       The original incoming XML presence stanza.
     * @param group     The Data Transfer Object representing the current room state.
     * @param sender    The MUC member profile of the person joining.
     */
    public void handleMemberPresence(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) { 
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        
        // 1. Send "Self-Presence" back to the joiner.
        // XMPP clients require status code 110 to recognize their own nickname in the room.
        String selfPresenceXml = buildSelfPresenceSuccess(roomBareJid, sender.getUserKey(), senderJid, sender.getRole());
        clusterMessagePublisher.convertAndSendToUser(
            UUID.randomUUID().toString(), 
            sender.getUserKey(), 
            sender.getUserKey(), 
            ChatType.GROUPCHAT, 
            selfPresenceXml
        );
        
        // 2. Broadcast the joiner's availability to all members in the room.
        // This includes updating the joiner's view of existing members (Synchronizing State).
        for(MucMember receiverMucMember : group.getMembers()) {
            String toUserKey = receiverMucMember.getUserKey();
            String availablePresence = buildOccupantPresence(
                roomBareJid, 
                sender.getUserKey(), 
                sender.getRole(), 
                jidUtil.getBareJid(toUserKey)
            );               
            
            clusterMessagePublisher.convertAndSendToUser(
                UUID.randomUUID().toString(), 
                toUserKey, 
                sender.getUserKey(), 
                ChatType.GROUPCHAT, 
                availablePresence
            );
        }
        
        log.debug("Presence synchronization complete for user {} in room {}", sender.getUserKey(), roomBareJid);
    }
    
    /**
     * Constructs the specific presence stanza used to acknowledge the sender's own entry.
     * * @param roomBareJid The bare JID of the room (room@conference.domain).
     * @param userKey     The nickname used by the occupant.
     * @param userJid     The real JID of the occupant.
     * @param affiliation The persistent affiliation (e.g., owner, admin, member).
     * @return A formatted XML presence string with MUC status code 110.
     */
    private String buildSelfPresenceSuccess(String roomBareJid, String userKey, String userJid, String affiliation) {
        String role = MucRoleUtil.getMucRole(affiliation).getValue();

        return String.format(
                "<presence from='%s/%s' to='%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' jid='%s'/>" +
                "    <status code='110'/>" + // 110: Inform user that presence refers to them
                "  </x>" +
                "</presence>",
                roomBareJid, userKey, userJid, affiliation, role, userJid
        );
    }
    
    /**
     * Constructs a general presence stanza for broadcasting to other room occupants.
     * Note: Per XEP-0045, the real JID is typically only shared in non-anonymous rooms.
     * * @param roomBareJid The bare JID of the room.
     * @param userKey     The nickname of the user whose presence is being broadcast.
     * @param affiliation The affiliation of that user.
     * @param userJid     The real JID of the user (used for internal routing/logging).
     * @return A formatted XML presence string.
     */
    private String buildOccupantPresence(String roomBareJid, String userKey, String affiliation, String userJid) {
        String role = MucRoleUtil.getMucRole(affiliation).getValue();

        return String.format(
                "<presence from='%s/%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' />" +
                "  </x>" +
                "</presence>",
                roomBareJid, userKey, affiliation, role
        );
    }  
}
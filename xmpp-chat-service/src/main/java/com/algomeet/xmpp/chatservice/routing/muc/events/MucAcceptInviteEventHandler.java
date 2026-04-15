package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler for processing accepted group invitations.
 * This class manages the transition of a user from 'invited' to 'occupant' by:
 * 1. Synchronizing MUC presence (XEP-0045).
 * 2. Logging and broadcasting a system join message.
 * 3. Archiving the join event for message history (MAM).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucAcceptInviteEventHandler {

    private final ClusterMessagePublisher clusterMessagePublisher;
    private final XmppArchiveService xmppArchiveService;
    private final DomainProperties domainProperties;
    private final MucMessageRouter xmppBroadCastHandler;
    private final MucMessageRouter mucMessageRouter;

    /**
     * Entry point for handling an invitation acceptance. 
     * Orchestrates presence synchronization and system notification.
     */
    public void handleAcceptedInvite(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) { 
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        
        // 1. Send Self-Presence acknowledgment to the joining member.
        // The Status 110 code is mandatory for the client to confirm its own session join.
        String selfPresenceXml = buildSelfPresenceSuccess(roomBareJid, sender.getUserKey(), senderJid, sender.getRole());
        clusterMessagePublisher.convertAndSendToUser(UUID.randomUUID().toString(), sender.getUserKey(), sender.getUserKey(), ChatType.GROUPCHAT, selfPresenceXml);
        
        // 2. Notify all existing members of the new occupant and sync occupant list for the joiner.
        String availablePresence = buildOccupantPresence(roomBareJid, sender.getUserKey(), sender.getRole(), senderJid);  
        mucMessageRouter.broadcastToOccupants(UUID.randomUUID().toString(), sender.getUserKey(), group, availablePresence, false);
                      
        // 3. Prepare a system message to log the join event in the chat stream.
        String stanzaId = UUID.randomUUID().toString();
        String body = sender.getUsername() + " has joined the group";
        String logXml = buildAcceptInviteLog(roomBareJid, body, sender.getUserKey(), senderJid);
        
        // 4. Persistence: Archive the join event to the database for future Message Archive Management (MAM) queries.
        saveToDatabase(stanzaId, roomJid, senderJid, group, sender, logXml);
        
        // 5. Broadcast: Real-time notification to all online occupants via the MessageRouter.
        xmppBroadCastHandler.broadcastToOccupants(ctx, stanzaId, roomJid, senderJid, XmppMessageType.GROUPCHAT, group, sender, null, logXml, logXml);
        
        log.info("User {} successfully joined room {} via invitation", senderJid, roomBareJid);
    }
    
    /**
     * Generates a presence stanza with status code 110.
     * Tells the client: "This presence stanza contains information about yourself."
     */
    private String buildSelfPresenceSuccess(String roomBareJid, String userKey, String userJid, String affiliation) {
        String role = MucRoleUtil.getMucRole(affiliation).getValue();

        return String.format(
                "<presence from='%s/%s' to='%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' jid='%s'/>" +
                "    <status code='110'/>" +
                "  </x>" +
                "</presence>",
                roomBareJid, userKey, userJid, affiliation, role, userJid
        );
    }
    
    /**
     * Generates standard occupant presence for broadcasting to the room.
     */
    private String buildOccupantPresence(String roomBareJid, String userKey, String affiliation, String userJid) {
        String role = MucRoleUtil.getMucRole(affiliation).getValue();

        return String.format(
                "<presence from='%s/%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' jid='%s'/>" +
                "  </x>" +
                "</presence>",
                roomBareJid, userKey,  affiliation, role, userJid
        );
    }
    
    /**
     * Persists the join event to the archive. 
     * Injects a unique Stanza-ID (XEP-0359) using a monotonic ULID for stable ordering.
     */
    private void saveToDatabase(String id, String roomBareJid, String senderJid, MucRoomDto group, MucMember sender, String xml) {
        StanzaInfo info = StanzaInfo.builder()
                .stanzaId(id)
                .stanzaType(XmppMessageType.GROUPCHAT.getXmlValue())
                .build();
        
        // Generate a ULID for chronological sorting and deduplication
        String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
        
        // XEP-0359: Unique and Stable Stanza IDs. Crucial for MAM and client sync.
        String stanzaIdExtension = String.format("<stanza-id xmlns='urn:xmpp:sid:0' by='%s' id='%s'/>", 
                domainProperties.getDomain(), ulidString);
        
        // Append the stanza-id before closing the message tag
        String enrichedXml = xml.replace("</message>", stanzaIdExtension + "</message>");

        xmppArchiveService.archiveEvent(enrichedXml, info, roomBareJid, null, 
                senderJid, ulidString)
        .doOnError(error -> {
            log.error("Failed to archive join event for {} in room {}", senderJid, roomBareJid, error);
        })
        .subscribe();
    }
    
    /**
     * Builds a system message stanza with a custom 'member_joined' event extension.
     * This allows UI clients to render a "User joined" notification instead of a standard chat bubble.
     */
    private String buildAcceptInviteLog(String roomJid, String body, String user, String userJid) {
        return String.format(
                "<message from='%s' to='%s' type='groupchat'>" +
                "  <body>%s</body>" +
                "  <x xmlns='http://algomeet.app/protocol/system'>" +
                "    <event type='member_joined' jid='%s'/>" + // Fixed: used %s directly
                "  </x>" +
                "</message>",
                roomJid, roomJid, body, userJid
        );
    }
}
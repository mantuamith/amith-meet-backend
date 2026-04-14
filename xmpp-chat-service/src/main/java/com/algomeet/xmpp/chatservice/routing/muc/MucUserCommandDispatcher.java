package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

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
    private final JidUtil jidUtil;
    private final XmppArchiveService xmppArchiveService;
	private final DomainProperties domainProperties;
	private final XmppBroadCastHandler xmppBroadCastHandler;

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
    	MucRoomDto group = groupCacheService.getCachedGroup(XmppUtil.getRoomId(roomJid), true);
    	Optional<MucMember> senderMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(principal.getUserKey()))
				.findFirst();
    	
    	if (inviteAcceptedRequest(xml)) {
    		handleAcceptedInvited(ctx,  roomJid, senderJid,  xml, group, senderMucMember.get());
    	} else {
    		handleChangeNicknameRequest(ctx, roomJid, senderJid, xml, group, senderMucMember.get());
    	}
    }

    
    private boolean inviteAcceptedRequest(String xml) {
    	return xml.contains("http://jabber.org/protocol/muc");
    }
    
    private void handleAcceptedInvited(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {    	
    	String selfPresenceXml = buildSelfPresenceSuccess(roomJid, sender.getUserKey(), senderJid, sender.getRole());
    	clusterMessagePublisher.convertAndSendToUser(UUID.randomUUID().toString(), sender.getUserKey(), sender.getUserKey(), ChatType.GROUPCHAT, selfPresenceXml);
    	
    	 for(MucMember receiverMucMember : group.getMembers()) {
    		 String toUserKey = receiverMucMember.getUserKey();
    		 String availablePresence = buildOccupantPresence(roomJid, sender.getUserKey(), sender.getRole(), jidUtil.getBareJid(toUserKey));    		 
             
             clusterMessagePublisher.convertAndSendToUser(UUID.randomUUID().toString(), toUserKey, sender.getUserKey(), ChatType.GROUPCHAT, availablePresence);
         }
    	     	 
    	// Broadcast added user message for logging
 		String stanzaId = UUID.randomUUID().toString();
 		String body = sender.getUsername() + " has joined the group";
 		 String logXml = buildAcceptInviteLog(roomJid, body, sender.getUserKey(), senderJid);
 		
 		// Save to database 
 		saveToDatabase(stanzaId, roomJid, senderJid, group, sender, xml);
 		
 		// Broadcast message
 		xmppBroadCastHandler.broadcastToOccupants(ctx, stanzaId, roomJid, senderJid, XmppMessageType.GROUPCHAT, group, sender, null, logXml, logXml);
    }
    
    /**
     * Builds the self-presence acknowledgment (Status 110).
     * Sent by the server to the joining user to confirm successful entry.
     */
    private String buildSelfPresenceSuccess(String roomJid, String nick, String userJid, String affiliation) {
        // Determine role based on affiliation using your existing utility
        String role = MucRoleUtil.getMucRole(affiliation).getValue();

        return String.format(
                "<presence from='%s/%s' to='%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' jid='%s'/>" +
                "    <status code='110'/>" +
                "  </x>" +
                "</presence>",
                roomJid, nick, userJid, affiliation, role, userJid
        );
    }
    
    /**
     * Builds the presence stanza to notify other room members about an occupant.
     * Sent to everyone EXCEPT the user described in the stanza.
     */
    private String buildOccupantPresence(String roomJid, String nick, String affiliation, String userJid) {
        // Determine role based on affiliation
        String role = MucRoleUtil.getMucRole(affiliation).getValue();

        return String.format(
                "<presence from='%s/%s' to='%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <item affiliation='%s' role='%s' jid='%s'/>" +
                "  </x>" +
                "</presence>",
                roomJid, nick,  affiliation, role, userJid
        );
    }
    

	private void saveToDatabase(String id, String roomBareJid, String senderJid, MucRoomDto group, MucMember sender, String xml) {
		StanzaInfo info = StanzaInfo.builder()
				.stanzaId(id)
				.stanzaType(XmppMessageType.GROUPCHAT.getXmlValue())
				.build();
		
		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
		
		// Inject Stanza-ID (XEP-0359) to facilitate client-side de-duplication and synchronization
		String stanzaIdExtension = String.format("<stanza-id xmlns='urn:xmpp:sid:0' by='%s' id='%s'/>", 
				domainProperties.getDomain(), ulidString);
		xml = xml.replace("</message>", stanzaIdExtension + "</message>");

		xmppArchiveService.archiveEvent(xml, info, roomBareJid, null, 
				senderJid, ulidString);
	}
    
    private String buildAcceptInviteLog(String roomJid,String body, String userNick, String userJid) {
        return String.format(
                "<message from='%s' type='groupchat'>" +
                "  <body>%s</body>" +
                "  <x xmlns='http://algomeet.app/protocol/system'>" +
                "    <event type='member_joined' jid='%%s'/>" +
                "  </x>" +
                "</message>",
                roomJid, body, userNick, userJid
        );
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
        String renamePresence = buildRenamePresence(roomBareJid, sender.getUserKey(), newNickname, mucAffiliation,
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
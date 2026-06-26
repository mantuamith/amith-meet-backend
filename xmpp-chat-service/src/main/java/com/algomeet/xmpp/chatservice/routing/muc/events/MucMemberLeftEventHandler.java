package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.MucEventType;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.PresenceStatusCode;
import com.algomeet.xmpp.chatservice.enums.PresenceType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.publisher.ExitGroupMemberMediaCleanupEventPublisher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.stanza.events.MucSystemEventLogMessageStanza;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class MucMemberLeftEventHandler {
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final MucMessageRouter mucMessageRouter;
    private final JidUtil jidUtil;
    private final DomainProperties domainProperties;
	private final MucMessageRouter xmppBroadCastHandler;
	private final XmppArchiveService xmppArchiveService;
	private final ExitGroupMemberMediaCleanupEventPublisher exitGroupMemberMediaCleanupEventPublisher;
    
    /**
     * Handles the left event of a member from a room by broadcasting presence and generate logs.
     * @param ctx       The Netty channel context for the current session.
     * @param roomJid   The full JID of the room.
     * @param xml       The original incoming XML presence stanza.
     * @param group     The Data Transfer Object representing the current room state.
     * @param principal 
     */
    public void handleMemberLeftRoom(ChannelHandlerContext ctx, String roomJid, String xml, Group group, XmppPrincipal principal) { 	        
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        
        // 1. Send "Self-Presence" back to the leaving user.
        // XMPP clients require status code 110 to recognize their own nickname in the room.        
        String selfPresenceXml = MucUserPresenceBuilder
        		.create()
        		.from(roomJid, principal.getUserKey()) // Resource-part is the member's room identity
        		.type(PresenceType.UNAVAILABLE.getValue())
				.affiliation(MucAffiliation.NONE.getValue())
				.statusCode(PresenceStatusCode.OWN_PRESENCE.getCode())
				.role(MucRole.NONE.getValue())
        		.build();
        
        clusterMessagePublisher.convertAndSendToUser(
        	UuidCreator.getTimeOrderedEpoch().toString(), 
            principal.getUserKey(), 
            principal.getUserKey(), 
            ChatType.GROUPCHAT, 
            selfPresenceXml
        );
        
        // 2. Broadcast the joiner's availability to all members in the room.
        // This includes updating the joiner's view of existing members (Synchronizing State).       
        String presenceXml = MucUserPresenceBuilder
				.create()
				.type(PresenceType.UNAVAILABLE.getValue())
				.from(roomJid, principal.getUserKey()) // Resource-part is the member's room identity
				.affiliation(MucAffiliation.NONE.getValue())
				.role(MucRole.NONE.getValue())
				.build();
        
        mucMessageRouter.broadcastToOccupants(UuidCreator.getTimeOrderedEpoch().toString(), principal.getUserKey(), group, presenceXml, false);
        
        /**
		 * ----------------------------------------------------------
		 * Build system log message
		 * ----------------------------------------------------------
		 * Human-readable audit trail message.
		 */
		String messageId = UuidCreator.getTimeOrderedEpoch().toString();

		String body = principal.getUsername() + " left";
	
		String senderJid = jidUtil.getBareJid(principal.getUserKey());
        String xmlLogStanza = buildMemberLeftLogStanza(
        		messageId,
        		senderJid,
				roomBareJid,
				body,
				senderJid);

		/**
		 * ----------------------------------------------------------
		 * Persist event (Message Archive Management)
		 * ----------------------------------------------------------
		 * Ensures historical traceability of room changes.
		 */
		UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
		// Insert stanza ID
		String forArchiveXmlLog = XmppStanzaUtil.insertStanzaId(xmlLogStanza, stanzaId.toString(), domainProperties.getDomain());
		
		saveToDatabase(messageId, roomBareJid, senderJid, group, principal, stanzaId, forArchiveXmlLog, group.getMessageRetentionDays());

		/**
		 * ----------------------------------------------------------
		 * 7. Broadcast system message to room
		 * ----------------------------------------------------------
		 * This is visible chat history event.
		 */
		xmppBroadCastHandler.broadcastToOccupants(
				ctx,
				messageId,
				roomBareJid,
				senderJid,
				XmppMessageType.GROUPCHAT,
				group,
				null,
				forArchiveXmlLog);
		
		// Cleanup up member group messages media files		
		exitGroupMemberMediaCleanupEventPublisher.publish(
				UUID.fromString(XmppUtil.getRoomId(roomJid)), UUID.fromString(principal.getUserKey()));
        
        log.debug("User left the room presence synchronization is completed for user {} in room {}", principal.getUserKey(), roomBareJid);
    }
    
    private String buildMemberLeftLogStanza(
			String id,
			String fromJid,
			String roomJid,
			String body,
			String leftUserJid) {
    	
    	return MucSystemEventLogMessageStanza.builder()
				.id(id)
				.from(fromJid)
				.to(roomJid)
				.body(body)
				.eventType(MucEventType.MEMBER_LEFT)
				.eventJid(leftUserJid)
				.build()
				.toXml();	
	}
    
    private void saveToDatabase(
			String id,
			String roomBareJid,
			String senderJid,
			Group group,
			XmppPrincipal principal,
			UUID stanzaId,
			String xml,
			Integer messageRetentionDays) {

		xmppArchiveService.archiveEvent(
				xml,
				id,
				XmppUtil.getRoomId(roomBareJid),
				null,
				principal.getUserKey(),
				stanzaId,
				messageRetentionDays);
	}
}
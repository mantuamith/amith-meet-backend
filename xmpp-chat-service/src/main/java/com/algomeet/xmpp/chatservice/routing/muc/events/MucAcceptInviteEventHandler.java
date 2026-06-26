package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucEventType;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.MucPresenceService;
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
    private final JidUtil jidUtil;
    private final MucPresenceService mucPresenceService;

    /**
     * Entry point for handling an invitation acceptance. 
     * Orchestrates presence synchronization and system notification.
     */
    public void handleAcceptedInvite(ChannelHandlerContext ctx, String roomJid, String xml, Group group, GroupMember sender) { 
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        String senderJid = jidUtil.getBareJid(sender.getUserKey());
        
        // 1. Send Self-Presence acknowledgment to the joining member.
        // The Status 110 code is mandatory for the client to confirm its own session join.
        String selfPresenceXml = MucUserPresenceBuilder
        		.create()
        		.from(roomJid, sender.getUserKey()) // Resource-part is the member's room identity
				.affiliation(sender.getRole())
				.role(MucRole.fromString(sender.getRole()).getValue())
				.statusCode(110)
        		.build();
        
        clusterMessagePublisher.convertAndSendToUser(UuidCreator.getTimeOrderedEpoch().toString(), sender.getUserKey(), sender.getUserKey(), ChatType.GROUPCHAT, selfPresenceXml);
        
        // 2. Notify all existing members of the new occupant and sync occupant list for the joiner.        
        String presenceXml = MucUserPresenceBuilder
				.create()
				.from(roomJid, sender.getUserKey()) // Resource-part is the member's room identity
				.affiliation(sender.getRole())
				.role(MucRole.fromString(sender.getRole()).getValue())
				.build();
        
        mucMessageRouter.broadcastToOccupants(UuidCreator.getTimeOrderedEpoch().toString(), sender.getUserKey(), group, presenceXml, false);
                      
        // 3. Prepare a system message to log the join event in the chat stream.
        String messageId = UuidCreator.getTimeOrderedEpoch().toString();
        String body = sender.getUsername() + " joined";
        String acceptedInvitationLogXml = buildAcceptInviteLog(messageId, roomBareJid, body, sender.getUserKey(), senderJid);
        
        UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
		// Insert stanza ID
		String forArchiveLogXml = XmppStanzaUtil.insertStanzaId(acceptedInvitationLogXml, stanzaId.toString(), domainProperties.getDomain());
        
        // 4. Persistence: Archive the join event to the database for future Message Archive Management (MAM) queries.
        saveToDatabase(messageId, roomJid, sender, stanzaId, forArchiveLogXml, group.getMessageRetentionDays());
        
        // 5. Broadcast: Real-time notification to all online occupants via the MessageRouter.
        xmppBroadCastHandler.broadcastToOccupants(ctx, messageId, roomJid, senderJid, XmppMessageType.GROUPCHAT, group, null, forArchiveLogXml);
                
        // Push group members presence to user
        mucPresenceService.pushGroupParticipantsPresenceToUser(ctx, group, sender.getUserKey());
        
        log.info("User {} successfully joined room {} via invitation", senderJid, roomBareJid);
    }
    
    /**
     * Persists the join event to the archive. 
     * Injects a unique Stanza-ID (XEP-0359) using a monotonic UUIDv7 for stable ordering.
     */
    private void saveToDatabase(String id, String roomBareJid, GroupMember sender, UUID stanzaId, String xml, Integer messageRetentionDays) {      

        xmppArchiveService.archiveEvent(xml, id, XmppUtil.getRoomId(roomBareJid), null, 
        		sender.getUserKey(), stanzaId, messageRetentionDays)
        .doOnError(error -> {
            log.error("Failed to archive join event for {} in room {}", sender.getUserKey(), roomBareJid, error);
        })
        .subscribe();
    }
    
    /**
     * Builds a system message stanza with a custom 'member_joined' event extension.
     * This allows UI clients to render a "User joined" notification instead of a standard chat bubble.
     */
    private String buildAcceptInviteLog(String id, String roomJid, String body, String user, String userJid) {
    	return MucSystemEventLogMessageStanza.builder()
				.id(id)
				.from(userJid)
				.to(roomJid)
				.body(body)
				.eventType(MucEventType.MEMBER_ACCEPTED_INVITE)
				.eventJid(userJid)
				.build()
				.toXml();
    }
}
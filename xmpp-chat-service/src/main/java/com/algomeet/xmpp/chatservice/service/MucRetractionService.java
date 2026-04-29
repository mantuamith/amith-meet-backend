package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.stanza.MessageRetractStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppRetractUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the retraction (deletion) of messages within Multi-User Chat (MUC) rooms.
 * <p>
 * This service implements the logic for <b>XEP-0424: Message Retraction</b>. It ensures that 
 * message removal is authorized, consistent across the MAM (Message Archive Management) 
 * database, and synchronized across all connected cluster nodes.
 * </p>
 * * @author Algomeet Core Team
 * @version 1.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucRetractionService {    
    private final GroupCacheService groupCacheService;
    private final XmppArchiveService xmppArchiveService;
    private final XmppRetractUtil xmppRetractUtil;
    private final JidUtil jidUtil;
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final DomainProperties domainProperties;
    private final XmppUtil xmppUtil;

    /**
     * Processes an incoming message retraction request.
     * <p>
     * Performs a security check to verify the requester is the original sender, 
     * deletes the record from the archive, and triggers a broadcast to all room occupants.
     * </p>
     *
     * @param ctx       The Netty channel context for the active session.
     * @param id        Internal tracking/trace ID for the request.
     * @param stanzaId  The unique XMPP stanza ID.
     * @param roomJid   The JID of the MUC room.
     * @param fromJid   The JID of the sender.
     * @param xml       The raw XML payload of the retraction request.
     * @param principal The authenticated security principal.
     */
    public void retract(ChannelHandlerContext ctx, String id, String stanzaId, String roomJid, String fromJid, String xml, XmppPrincipal principal) {
        // Set tenant context to ensure data isolation in the shared database
        TenantContext.setCurrentTenant(principal.getTenantId());		

        // Fetch room metadata to identify members for the broadcast
        MucRoomDto group = groupCacheService.getCachedGroup(XmppUtil.getRoomId(roomJid));
        
        // Extract the target message ID (the 'retracted-id') from the XML payload
        String retractMessageId = xmppRetractUtil.getRetractMessageId(xml);

        if (StringUtils.hasText(retractMessageId)) {
            xmppArchiveService.findById(retractMessageId)
            .<Void>flatMap(message -> { 
                // Authorization: Validate that the initiator is the one who sent the original message
                if (message.getFrom().equalsIgnoreCase(principal.getUserKey())) {

                    log.info("Executing retraction: Message {} in room {} by user {}", 
                            retractMessageId, roomJid, principal.getUserKey());

                    // Soft delete from MAM archive so the message is not returned in future history fetches
                    message.setDeletedAt(Instant.now());
                    message.setStanzaXml(XmppStanzaUtil.removeBodyTag(message.getStanzaXml()));
                    
                    return xmppArchiveService.save(message)
                            .doOnSuccess(success -> {
                                // Inform all online occupants via a broadcasted retraction stanza
                                composeAndSendRetractStanza(ctx, id, stanzaId, roomJid, group, retractMessageId, principal);                                
                                // TODO: Implementation required for decrementing unread message counters
                            })
                            .then();
                } else {
                    // Security Breach: Attempt to retract a message owned by someone else
                    log.warn("Unauthorized retraction attempt: User {} tried to retract message {} (Owner: {})", 
                            principal.getUserKey(), retractMessageId, message.getFrom());

                    xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), 
                            XmppErrorType.CANCEL, XmppErrorConditions.FORBIDDEN, "You are not authorized to retract this message");

                    return Mono.empty();
                }
            })
            .subscribe(); // Execute the reactive pipeline
        }
    }

    /**
     * Constructs and dispatches the XEP-0424 compliant retraction stanza to all room members.
     * <p>
     * The stanza is routed through the {@link ClusterMessagePublisher} to ensure delivery 
     * to occupants connected to different cluster nodes.
     * </p>
     * * @param ctx              The channel context.
     * @param id               The tracking ID.
     * @param stanzaId         The original stanza ID to be mapped to the new broadcast.
     * @param roomJid          The JID of the MUC room.
     * @param group            Metadata of the room containing the current member list.
     * @param retractMessageId The ID of the message to be removed from clients.
     * @param principal        The initiator's principal.
     */
    private void composeAndSendRetractStanza(ChannelHandlerContext ctx, String id, String stanzaId, String roomJid, 
            MucRoomDto group, String retractMessageId, XmppPrincipal principal) {
        
        // Standard UTC timestamp for the retraction event
        String stamp = Instant.now().toString();

        group.getMembers().forEach(m -> {
            // Build the retraction stanza targeting each member's bare JID
            MessageRetractStanza retractStanza = MessageRetractStanza.builder()
                    .id(id)
                    .to(jidUtil.getBareJid(m.getUserKey()))
                    .from(roomJid + "/" + principal.getUserKey())				
                    .by(roomJid + "/" + principal.getUserKey())
                    .retractedId(retractMessageId)
                    .type(XmppMessageType.GROUPCHAT.getXmlValue())
                    .stamp(stamp)
                    .build();

            // Inject the server-generated stanza ID for auditing/tracking
            String xml = XmppStanzaUtil.insertStanzaId(retractStanza.toXml(), stanzaId, principal.getDomain());

            // Dispatch through the cluster layer
            clusterMessagePublisher.convertAndSendToUser(id, m.getUserKey(), principal.getUserKey(), 
                    ChatType.GROUPCHAT, false, true, xml, principal);
        });				
    }	
}
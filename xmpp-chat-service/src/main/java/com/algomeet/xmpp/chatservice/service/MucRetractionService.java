package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.common.dto.Group;
import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.publisher.DeleteMessageMediaEventPublisher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.stanza.MessageRetractStanza;
import com.algomeet.xmpp.chatservice.util.RetractUtil;
import com.algomeet.xmpp.chatservice.util.XmppRetractUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    private final AbstractGroupCache groupCacheService;
    private final XmppArchiveService xmppArchiveService;
    private final XmppRetractUtil xmppRetractUtil;
    private final MucMessageRouter mucMessageRouter;
    private final DomainProperties domainProperties;
    private final XmppUtil xmppUtil;
    private final RetractUtil retractUtil;
    private final DeleteMessageMediaEventPublisher messageMediaDeleteEventPublisher;

    /**
     * Processes an incoming message retraction request.
     * <p>
     * Performs a security check to verify the requester is the original sender, 
     * deletes the record from the archive, and triggers a broadcast to all room occupants.
     * </p>
     *
     * @param ctx       The Netty channel context for the active session.
     * @param id        Internal tracking/trace ID for the request.
     * @param roomJid   The JID of the MUC room.
     * @param xml       The raw XML payload of the retraction request.
     * @param principal The authenticated security principal.
     * @return A {@link Mono<Void>} signaling pipeline completion.
     */
    public Mono<Void> retract(ChannelHandlerContext ctx, String id, String roomJid, String xml, XmppPrincipal principal) {
        // Extract the target message ID (the 'retracted-id') from the XML payload
        String retractMessageId = xmppRetractUtil.getRetractMessageId(xml);
        if (!StringUtils.hasText(retractMessageId)) {
            return Mono.empty();
        }

        // Offload the blocking cache lookup and clear the ThreadLocal context securely
        return Mono.fromCallable(() -> {
            TenantContext.setCurrentTenant(principal.getTenantId());	
            try {
                return groupCacheService.getCachedGroup(XmppUtil.getRoomId(roomJid));
            } finally {
                TenantContext.clear();
            }
        })
        .subscribeOn(Schedulers.boundedElastic()) // Shield Netty EventLoop from blocking cache boundaries
        .flatMap((Group group) -> 
            xmppArchiveService.findByMessageId(UUID.fromString(retractMessageId))
                .flatMap((var message) -> { 
                    
                    // Authorization: Validate that the initiator is the one who sent the original message
                    if (message.getFrom().compareTo(UUID.fromString(principal.getUserKey())) == 0) {

                        log.info("Executing retraction: Message {} in room {} by user {}", 
                                retractMessageId, roomJid, principal.getUserKey());

                        String newString = "<body>This message was deleted</body>";
                        UUID updateCursorId = UuidCreator.getTimeOrderedEpoch();
                        
                        // Soft delete from MAM archive so the message is not returned in future history fetches
                        message.setDeletedAt(Instant.now().toEpochMilli());
                        message.setUpdateCursorId(updateCursorId);
                        message.setCountable(false);
                        message.setStanzaXml(XmppStanzaUtil.markAsRetractedStanza(message.getStanzaXml(), newString));
                        
                        Mono<Void> mediaDeletePipeline = Mono.empty();
                        // Check if message has media files, if true then revoke sender and receiver access to media file(s)
                        if (!CollectionUtils.isEmpty(message.getMediaIds())) {
                            mediaDeletePipeline = messageMediaDeleteEventPublisher.publish(
                                principal.getUserKey(), 
                                message.getMediaIds().stream().map(Object::toString).collect(Collectors.toSet()), 
                                null,
                                group.getId().toString(), 
                                retractMessageId
                            ).then();
                        }
                        
                        return mediaDeletePipeline.then(xmppArchiveService.save(message))
                                .flatMap(success -> 
                                    // Inform all online occupants via a broadcasted retraction stanza
                                    composeAndSendRetractStanza(ctx, id, updateCursorId.toString(), roomJid, group, retractMessageId, principal)    
                                        // Delete/retract related messages sequentially
                                        .then(Mono.defer(() -> retractUtil.retractRelatedMessages(message.getRoomId(), message.getMessageId())))
                                )
                                .then();
                    } else {
                        // Security Breach: Attempt to retract a message owned by someone else
                        log.warn("Unauthorized retraction attempt: User {} tried to retract message {} (Owner: {})", 
                                principal.getUserKey(), retractMessageId, message.getFrom());

                        xmppUtil.sendError(ctx, id, principal.getBareJid(), domainProperties.getGroupChatDomain(), 
                                XmppErrorType.CANCEL, XmppErrorConditions.FORBIDDEN, "You are not authorized to retract this message");

                        return Mono.<Void>empty();
                    }
                })
        )
        .contextWrite(context -> context.put("tenantId", principal.getTenantId()));
    }

    /**
     * Constructs and dispatches the XEP-0424 compliant retraction stanza to all room members.
     * <p>
     * The stanza is routed through the {@link ClusterMessagePublisher} to ensure delivery 
     * to occupants connected to different cluster nodes.
     * </p>
     */
    private Mono<Void> composeAndSendRetractStanza(ChannelHandlerContext ctx, String id, String stanzaId, String roomJid, 
            Group group, String retractMessageId, XmppPrincipal principal) {
        
        // Standard UTC timestamp for the retraction event
        String stamp = Instant.now().toString();

        // Convert the synchronous iterative broadcast stream into an aggregation of asynchronous publishers
        return Mono.defer(() -> {
            var broadcasts = group.getMembers().stream().map(m -> {
                // Build the retraction stanza to be published for each member
                MessageRetractStanza retractStanza = MessageRetractStanza.builder()
                        .id(id)
                        .from(roomJid + "/" + principal.getUserKey())				
                        .retractedId(retractMessageId)
                        .type(XmppMessageType.GROUPCHAT.getXmlValue())
                        .stamp(stamp)
                        .build();

                // Inject the server-generated stanza ID for auditing/tracking
                String xml = XmppStanzaUtil.insertStanzaId(retractStanza.toXml(), stanzaId, principal.getDomain());

                // Publish to group member execution chain
                return mucMessageRouter.broadcastToOccupants(id, m.getUserKey(), group, xml, principal.getSessionId());
            }).collect(Collectors.toList());

            return Mono.when(broadcasts);
        });
    }	
}
package com.algomeet.xmpp.chatservice.routing;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.routing.handler.XmppSessionLifecycleHandler;
import com.algomeet.xmpp.chatservice.routing.handler.XmppStreamManagementHandler;
import com.algomeet.xmpp.chatservice.routing.handler.XmppInfoQueryHandler;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StanzaError;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>The primary entry point for processing and routing all incoming XMPP stanzas 
 * over WebSocket frames.</p>
 * 
 * <p>This handler manages the high-level orchestration of a stanza's lifecycle:</p>
 * <ul>
 *     <li><b>Validation:</b> Ensures the incoming payload is well-formed XML.</li>
 *     <li><b>Lifecycle Management:</b> Updates presence and chat states (XEP-0085).</li>
 *     <li><b>Stream Management:</b> Handles {@code <r/>} and {@code <a/>} elements for 
 *         XEP-0198 reliability.</li>
 *     <li><b>Direct Routing:</b> Persists messages to {@link OfflineMessageService} and 
 *         broadcasts to the cluster via {@link ClusterMessagePublisher}.</li>
 *     <li><b>Error Handling:</b> Returns standardized XMPP error stanzas for parsing 
 *         or persistence failures.</li>
 * </ul>
 * 
 * <p>This handler is {@link ChannelHandler.Sharable @Sharable}, meaning a single instance 
 * is used across all Netty channels. Per-session state is retrieved via 
 * {@link XmppSessionAttributes}.</p>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@ChannelHandler.Sharable
@Component
@AllArgsConstructor
public class XmppRoutingHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();
    
    private final XmppInfoQueryHandler xmppInfoQueryHandler;
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final XmppSessionLifecycleHandler chatStateNotificationHandler;
    private final OfflineMessageService offlineMessageService; 
    private final XmppStreamManagementHandler xmppStreamManagementHandler;
    private final UserSessionRegistry userSessionRegistry;
    private final NotificationService notificationService;

    /**
     * Entry point for incoming WebSocket text frames.
     * 
     * @param ctx   The channel context.
     * @param frame The WebSocket frame containing the XMPP XML string.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String xml = frame.text();
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();

        try {
            // 1. Lifecycle & Presence logic (XEP-0186 / XEP-0085)
            if (principal != null) {
                handleStatusUpdates(ctx, principal, xml);
            }

            // 2. Extract routing metadata without fully unmarshalling the whole stanza
            Map<String, String> attributes = parseStanzaAttributes(xml);
            String to = attributes.get("to");
            String from = attributes.get("from");
            String id = attributes.get("id");
            String type = attributes.get("type");

            // 3. Routing Logic Branching
            if (StringUtils.hasText(to)) {
                // Stanza has a recipient (Message/IQ/Presence directed to others)
                handleRouting(ctx, id, XmppUtil.getUserKey(to), XmppUtil.getUserKey(from), type, xml);
            } else {
                // Stanza is a control element directed at the server (Stream Mgmt or Service Discovery)
                if (xmppStreamManagementHandler.isAckMessage(xml)) {
                    xmppStreamManagementHandler.process(ctx, xml, principal);
                } else {
                    xmppInfoQueryHandler.handleQuery(ctx, xml);
                }
            }

        } catch (XMLStreamException e) {
            log.error("Malformed XML received: {} , {}", xml, e.getMessage());
            sendError(ctx, null, null, null, XmppErrorConditions.NOT_WELL_FORMED, "XML parsing failed");
        } catch (Exception e) {
             log.error("Routing error for XML {}: {}", xml, e.getMessage(), e);
        }
    }

    /**
     * Delegates status, presence, and chat state updates to the lifecycle handler.
     */   
    private void handleStatusUpdates(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
        chatStateNotificationHandler.handleStatusUpdates(ctx, principal, xml);
    }

    /**
     * Performs a lightweight parse of the top-level element attributes (to, from, id, type).
     * This avoids expensive full XML unmarshalling for simple routing decisions.
     */
    private Map<String, String> parseStanzaAttributes(String xml) throws XMLStreamException {
        Map<String, String> attrMap = new HashMap<>();
        try (StringReader sr = new StringReader(xml)) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(sr);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            attrMap.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                        }
                        break;
                    }
                }
            } finally {
                reader.close();
            }
        }
        return attrMap;
    }
    
    /**
     * Determines if the routing is for a 1-to-1 chat or a Multi-User Chat (Group).
     */
    public void handleRouting(ChannelHandlerContext ctx, String id, String to, String from, String type, String originalXml) {   	
        if ("groupchat".equalsIgnoreCase(type)) {
            handleGroupChatRouting(id, to, from, originalXml);
        } else {       	
            handleDirectChatRouting(ctx, id, to, from, type, originalXml);
        }
    }       

    /**
     * Handles 1-to-1 message routing, persistence for offline storage, 
     * and XEP-0198 inbound acknowledgments.
     */
    private void handleDirectChatRouting(ChannelHandlerContext ctx, String id, String to, String from, String type, String originalXml) {
        AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();
        
        // Only save to DB if the message type warrants storage (e.g., 'chat')
        if (XmppMessageType.fromString(type).supportsOfflineStorage()) {
            offlineMessageService.save(id, to, from, type, originalXml)
                .doOnSuccess(savedDoc -> {
                    // Send an immediate <a h='x'/> to the sender once persisted
                    if (handledCount != null) {
                        ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(handledCount.incrementAndGet()).toXml()));
                    }
                })
                .doOnError(e -> {
                    log.error("Failed to persist message {}: {}", id, e.getMessage());
                    sendError(ctx, id, from, to, XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
                })
                .subscribe();
        }
        
        // Retrieve user sessions
        Set<UserSession>  userSessions = userSessionRegistry.getSessions(to);       
        
        // Check if user is online, no need to sync the message to other nodes if user is offline
        if (!(CollectionUtils.isEmpty(userSessions))) {
        	
        	// Check if any of user session has active status, otherwise send notification
        	if (!(userSessions.parallelStream()
        			.anyMatch(session -> UserState.ACTIVE == session.getState()))) {
        		
        		// Send push notification
                sendPushNotification(to, XmppUtil.getMessageBody(originalXml), NotificationType.DIRECT_MESSAGE);
        	}
        	
        	// Publish to Redis/Cluster topic so the recipient's node can deliver it
            clusterMessagePublisher.convertAndSendToUser(id, to, from, originalXml);
        }         
    }

    /**
     * Handles routing for Multi-User Chat rooms.
     */
    private void handleGroupChatRouting(String id, String roomJid, String from, String originalXml) {
        // Implementation for MUC routing logic
    }

    /**
     * Utility to send standardized XMPP error frames back to the client.
     */
    private void sendError(ChannelHandlerContext ctx, String id, String to, String from, String condition, String text) {
        StanzaError error = new StanzaError(id, to, from, condition, text);
        ctx.writeAndFlush(new TextWebSocketFrame(error.toXml()));
    }
        
    /**
     * Used to send push notification for new message
     *
     * @param toKey
     * @param message
     * @param notifcationType
     */
    private void sendPushNotification(String toKey, String message, NotificationType notifcationType) {
            Notification notif = Notification.builder()
                    .receiverIds(Set.of(toKey))
                    .type(notifcationType)
                    .title("You have new message")
                    .body(message)
                    .tenantId(TenantContext.getCurrentTenant())
                    .build();

            notificationService.sendPush(notif);
    }
}
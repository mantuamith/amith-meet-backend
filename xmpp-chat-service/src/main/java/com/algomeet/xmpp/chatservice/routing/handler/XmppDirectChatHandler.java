package com.algomeet.xmpp.chatservice.routing.handler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
@Component
@AllArgsConstructor
public class XmppDirectChatHandler {
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final OfflineMessageService offlineMessageService; 
    private final UserSessionRegistry userSessionRegistry;
    private final NotificationService notificationService;
    private final JingleNotificationHandler jingleSessionOrchestrator;
    
	 /**
     * Handles 1-to-1 message routing, persistence for offline storage, 
     * and cluster-wide synchronization, and push notifications for offline users.
     */
    public void handleDirectChatRouting(ChannelHandlerContext ctx, String id, String to, String from, String type, String originalXml) {
        XmppMessageType msgType = XmppMessageType.fromString(type);
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
        
    	String toUserKey = XmppUtil.getUserKey(to);
    	String fromUserKey = principal.getUserKey();
        
        // 1. Persistence & XEP-0198 Acknowledgment
        if (msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(originalXml)) {
            offlineMessageService.save(id, toUserKey, fromUserKey, type, originalXml)
                .doOnSuccess(savedDoc -> {
                    // Acknowledge receipt to the sender (Server -> Client 'h' update)
                	AtomicBoolean isEnabledSm = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).get();
                    AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();
                    
                    if (isEnabledSm != null && isEnabledSm.get() && handledCount != null) {
                        long h = handledCount.incrementAndGet();
                        ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(h).toXml()));
                    }
                })
                .doOnError(e -> {
                    log.error("Storage failure for message {}: {}", id, e.getMessage());
                    XmppUtil.sendError(ctx, id, to, from, XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
                })
                .subscribe();
        }

        // 2. Cluster Routing & Notification Logic
        Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
        
        boolean hasSessions = !CollectionUtils.isEmpty(userSessions);
        boolean hasActiveSession = hasSessions && userSessions.stream()
                .anyMatch(s -> UserState.ACTIVE == s.getState());

        if (hasSessions) {
            // Broadast to Redis: Even if they are AWAY/DND, we attempt delivery 
            // to their active WebSocket channels across the cluster.
            clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, originalXml);
        }

        // 3. Push Notification Logic
        // Trigger push if the user has no sessions OR no session is currently 'ACTIVE'
        if (!hasActiveSession) {
            log.debug("User {} has no active sessions. Triggering push notification.", toUserKey);
            
            /*
             * Jingle Signaling Detection (XEP-0166)
             * We use a "Fast-Scan" approach using indexOf() to minimize CPU cycles.
             * 
             * - 'urn:xmpp:jingle:1': Ensures the stanza belongs to the Jingle namespace.
             */
            if (originalXml.indexOf("urn:xmpp:jingle:1") != -1) {      
            	// Handle Jingle Signaling notification
            	jingleSessionOrchestrator.handlePush(ctx, id, toUserKey, fromUserKey, originalXml, hasSessions, principal);
                
            } else {                 
                /*
                 * Standard Message Handling
                 * If the stanza is not a call initiation, treat it as a standard chat message.
                 * We extract the <body> element and trigger a Push Notification (FCM/APNs)
                 * to the recipient, ensuring they receive the message even if offline.
                 */            	
            	if (XmppStanzaUtil.isPushEligible(originalXml)) {
	                String body = XmppUtil.getMessageBody(originalXml);
	                sendPushNotification(toUserKey, body, NotificationType.DIRECT_MESSAGE, principal);
            	}
            }     
        }
    }

    /**
     * Used to send push notification for new message
     *
     * @param toKey
     * @param message
     * @param notifcationType
     */
    private void sendPushNotification(String toKey, String message, NotificationType notifcationType, XmppPrincipal principal) {
            Notification notif = Notification.builder()
                    .receiverIds(Set.of(toKey))
                    .type(notifcationType)
                    .title("You have new message")
                    .body(message)
                    .tenantId(principal.getTenantId())
                    .build();

            notificationService.sendPush(notif);
    }
}

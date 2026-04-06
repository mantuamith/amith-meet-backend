package com.algomeet.xmpp.chatservice.routing.chat;

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
import com.algomeet.xmpp.chatservice.routing.call.CallLifeCycleTracker;
import com.algomeet.xmpp.chatservice.routing.call.JingleNotificationHandler;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppStreamManagementUtil;
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
public class XmppChatHandler {
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final OfflineMessageService offlineMessageService; 
    private final UserSessionRegistry userSessionRegistry;
    private final NotificationService notificationService;
    private final JingleNotificationHandler jingleNotificationHandler;
    private final CallLifeCycleTracker callTracker;
    
	 /**
     * Handles 1-to-1 message routing, persistence for offline storage, 
     * and cluster-wide synchronization, and push notifications for offline users.
     */
    public void handleDirectChatRouting(ChannelHandlerContext ctx, String id, String toJid, String fromJid, String type, String originalXml) {
        XmppMessageType msgType = XmppMessageType.fromString(type);
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();               
        
    	String toUserKey = XmppUtil.getUserKey(toJid);
    	String fromUserKey = principal.getUserKey();
    	    	
		// Only scan the first 500 characters for routing/type info
		// Most XMPP metadata is at the start of the stanza
		String xmlHeader = originalXml.substring(0, Math.min(originalXml.length(), 500));
        
        // 1. Persistence & XEP-0198 Acknowledgment
        if (msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(xmlHeader, originalXml)) {
            offlineMessageService.save(id, toUserKey, fromUserKey, type, originalXml)
                .doOnSuccess(savedDoc -> {
                	// XEP-0198: Increment the inbound handled count and send 'h' ack to the sender
        			XmppStreamManagementUtil.incrementAndSendInboundH(ctx);                    
                })
                .doOnError(e -> {
                    log.error("Storage failure for message {}: {}", id, e.getMessage(), e);
                    XmppUtil.sendError(ctx, id, toJid, fromJid, XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
                })
                .subscribe();
        } else {
        	// XEP-0198: Increment the inbound handled count and send 'h' ack to the sender
			XmppStreamManagementUtil.incrementAndSendInboundH(ctx);  
        }

        // 2. Cluster Routing & Notification Logic
        Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
        
        boolean hasSessions = !CollectionUtils.isEmpty(userSessions);
        boolean hasActiveSession = hasSessions && userSessions.stream()
                .anyMatch(s -> UserState.ACTIVE == s.getState());
        
        // Handle call life cycle 
        if (XmppMessageType.SET == XmppMessageType.fromString(type) 
        		&& originalXml.contains("urn:xmpp:jingle:1")) {
        	callTracker.track(ctx, toJid, fromJid, originalXml, principal);
        }

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
             * 
             * - 'urn:xmpp:jingle:1': Ensures the stanza belongs to the Jingle namespace.
             */
            if (XmppMessageType.SET == XmppMessageType.fromString(type)
            		&& originalXml.contains("urn:xmpp:jingle:1")) {   
            	
            	// Handle Jingle Signaling notification
            	jingleNotificationHandler.handlePush(ctx, id, toUserKey, fromUserKey, originalXml, principal);
                
            } else {                 
                /*
                 * Standard Message Handling
                 * If the stanza is not a call initiation, treat it as a standard chat message.
                 * We extract the <body> element and trigger a Push Notification (FCM/APNs)
                 * to the recipient, ensuring they receive the message even if offline.
                 */            	
            	if (XmppStanzaUtil.isArchiveable(xmlHeader, originalXml)) {
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

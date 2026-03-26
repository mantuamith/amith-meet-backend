package com.algomeet.xmpp.chatservice.routing.handler;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.stanza.XmppErrorIq;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p><strong>Jingle Signaling & Call Orchestration Handler</strong></p>
 * 
 * <p>The {@code JingleSessionOrchestrator} is responsible for interpreting Jingle (XEP-0166) 
 * session initiation requests. It determines the appropriate delivery strategy based 
 * on the recipient's online status across the cluster.</p>
 * 
 * <p><b>Core Responsibilities:</b></p>
 * <ul>
 *     <li><b>Feature Detection:</b> Identifies if the request is a Video or Audio call.</li>
 *     <li><b>Push Notification Dispatch:</b> Triggers high-priority VoIP or MISSED_CALL 
 *         notifications via the {@code NotificationService}.</li>
 *     <li><b>Reliable Logging:</b> Converts failed call attempts into persistent 
 *         {@code <message type='headline'/>} stanzas for offline users.</li>
 *     <li><b>Error Feedback:</b> Provides immediate RFC-compliant IQ error responses 
 *         to the caller if delivery is impossible.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@AllArgsConstructor
public class JingleSessionOrchestrator {

    private final NotificationService notificationService;
    private final OfflineMessageService offlineMessageService;

    /**
     * Entry point for IQ routing. Analyzes the XML payload for media descriptions.
     * 
     * @param ctx         The Netty {@link ChannelHandlerContext} for the sender's connection.
     * @param id          The original IQ stanza ID.
     * @param to          The intended recipient's unique identifier.
     * @param from        The sender's unique identifier.
     * @param xml         The raw Jingle XML payload.
     * @param hasSessions Flag indicating if the recipient is currently connected to any cluster node.
     * @param principal   The security principal of the authenticated sender.
     */
    public void handleIqRouting(ChannelHandlerContext ctx, String id, String to, 
                                String from, String xml, boolean hasSessions, XmppPrincipal principal) {
        // Detect call type based on media attributes in the Jingle description
    	// Use a optimized check for media type (handles ' and ")
        boolean isVideo = xml.matches("(?s).*media=['\"]video['\"].*");
        boolean isAudio = xml.matches("(?s).*media=['\"]audio['\"].*");

        if (isVideo && isAudio) {
            handleCallLogic(ctx, id, to, from, hasSessions, principal, "video");
        } else if (isAudio) {
            handleCallLogic(ctx, id, to, from, hasSessions, principal, "audio");
        }
    }

    /**
     * Executes the business logic for call routing, including notification and persistence.
     * 
     * @param callType Either "video" or "audio".
     */
    private void handleCallLogic(ChannelHandlerContext ctx, String id, String to, String from, 
                                 boolean hasSessions, XmppPrincipal principal, String callType) {
        
        if (hasSessions) {
            // SCENARIO 1: Recipient is online. 
            // We notify them via push to wake up devices/show incoming call UI.
            NotificationType type = "video".equals(callType) ? NotificationType.VIDEO_CALL : NotificationType.AUDIO_CALL;
            String title = "video".equals(callType) ? "Incoming Video Call..." : "Incoming Call...";
            sendPush(to, type, title, principal.getTenantId());
            
            log.info("Call [{}] notification sent to active user: {}", callType, to);
        } else {
            // SCENARIO 2: Recipient is offline.
            // 1. Send "Missed Call" push immediately.
            NotificationType type = "video".equals(callType) ? NotificationType.VIDEO_MISSED_CALL : NotificationType.AUDIO_MISSED_CALL;
            String title = "Missed " + callType + " call from " + from;
            sendPush(to, type, title, principal.getTenantId());

            // 2. Persist a headline message so the user sees the missed call in their history upon login.
            String miscallId = "mc-" + UUID.randomUUID().toString();
            String miscallXml = constructMissedCallXml(from, to, miscallId, callType, id);
            
            offlineMessageService.save(miscallId, to, from, "headline", miscallXml)
                .doOnSuccess(s -> log.debug("Missed {} call persisted for user: {}", callType, to))
                .doOnError(e -> log.error("Storage failure for missed call: {}", e.getMessage()))
                .subscribe();

            // 3. Inform the caller's client that the recipient is unavailable.
            replyWithError(ctx, id, from, to);
        }
    }

    /**
     * Constructs an XMPP {@code <message type='headline'/>} stanza to notify a 
     * recipient of a missed audio or video call.
     * 
     * <p>This stanza uses the {@code headline} type to ensure it is treated as a 
     * notification rather than a standard chat message. It includes a custom 
     * {@code <call-log/>} extension for Algomeet-specific call history synchronization.</p>
     * 
     * @param from      The JID of the caller (initiator).
     * @param to        The JID of the intended recipient.
     * @param id        A unique stanza ID for tracking and archive (MAM) compatibility.
     * @param type      The media type of the call (e.g., "audio" or "video").
     * @param sid       The unique Jingle Session ID (XEP-0166) used to link this 
     *                  notification to the signaling session.
     * @return A formatted XML string representing the missed call notification.
     */
    private String constructMissedCallXml(String from, String to, String id, String type, String sid) {
        // Use current UTC epoch for the call-log metadata
        String timestamp = String.valueOf(System.currentTimeMillis());

        return String.format(
            "<message from='%s' to='%s' type='headline' id='%s'>" +
                "<subject>Missed %s Call</subject>" +
                "<body>You missed a %s call from %s</body>" +
                "<call-log xmlns='urn:xmpp:algomeet:calls' type='%s' status='missed' timestamp='%s' sid='%s'/>" +
            "</message>",
            from, to, id, type, type, from, type, timestamp, sid
        );
    }

    /**
     * Wrapper for the NotificationService to standardize push notification payloads.
     */
    private void sendPush(String to, NotificationType type, String title, Integer tenantId) {
        Notification notif = Notification.builder()
                .receiverIds(Set.of(to))
                .type(type)
                .title(title)
                .tenantId(tenantId)
                .build();
        notificationService.sendPush(notif);
    }

    /**
     * Sends an RFC-compliant 'service-unavailable' error response to the caller.
     */
    private void replyWithError(ChannelHandlerContext ctx, String id, String from, String to) {
        String errorXml = new XmppErrorIq(id, from, to, "User is offline. Call logged as missed.").toXml();
        ctx.writeAndFlush(new TextWebSocketFrame(errorXml));
    }
}
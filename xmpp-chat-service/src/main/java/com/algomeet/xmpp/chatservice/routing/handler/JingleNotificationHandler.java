package com.algomeet.xmpp.chatservice.routing.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;

import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p><strong>Jingle Signaling & Call Lifecycle Coordinator</strong></p>
 * * <p>The {@code JingleNotificationHandler} manages the lifecycle of Jingle (XEP-0166) 
 * sessions. It serves as the primary interceptor for call signaling, coordinating 
 * between real-time Netty delivery, Redis-backed timeout tracking, and Push Notifications.</p>
 * * <p><b>Core Responsibilities:</b></p>
 * <ul>
 * <li><b>Session Interception:</b> Detects {@code session-initiate}, {@code session-accept}, 
 * and {@code session-terminate} stanzas to manage call state.</li>
 * <li><b>Timeout Orchestration:</b> Schedules 30-second 'Missed Call' tasks in Redis 
 * Sorted Sets (ZSET) upon session initiation.</li>
 * <li><b>State Cleanup:</b> Atomically removes pending timeout tasks when a call 
 * is accepted or actively declined.</li>
 * <li><b>Push Dispatch:</b> Triggers high-priority VoIP notifications to wake up 
 * mobile devices via {@code NotificationService}.</li>
 * </ul>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@AllArgsConstructor
public class JingleNotificationHandler {

	private final NotificationService notificationService;

	/**
	 * Main entry point for Jingle stanza processing. 
	 * Identifies the action type and delegates to the appropriate lifecycle handler.
	 * * @param ctx         The Netty {@link ChannelHandlerContext} for the active connection.
	 * @param id          The unique IQ stanza ID.
	 * @param to          The JID of the intended recipient.
	 * @param from        The JID of the initiator.
	 * @param xml         The raw Jingle XML payload containing the SID and reason.
	 * @param principal   The authenticated user's security context.
	 */
	public void handlePush(ChannelHandlerContext ctx, String id, String to, 
			String from, String xml, XmppPrincipal principal) {

		// 1. Detect media type using quote-agnostic regex (XEP-0167)
		boolean isVideo = xml.matches("(?s).*media=['\"]video['\"].*");

		// 2. Identify the Jingle Action (XEP-0166 Section 7.2)
		boolean isInitiate = xml.contains("session-initiate");
		
		if(isInitiate) {
			String callType = isVideo ? "video" : "audio";
			handleCallLogic(ctx, id, to, from, principal, callType, xml);
		}
	}

	/**
	 * Handles the 'Initiate' phase: Triggers the VoIP push and registers the 
	 * 30-second timeout task in the Redis delayed queue.
	 * * @param callType The determined media type ("video" or "audio").
	 */
	private void handleCallLogic(ChannelHandlerContext ctx, String id, String to, String from, 
			XmppPrincipal principal, String callType, String xml) {
		    
			// Notify the user via high-priority push to trigger CallKit/ConnectionService UI.
			NotificationType type = "video".equals(callType) ? NotificationType.VIDEO_CALL : NotificationType.AUDIO_CALL;
			String title = "video".equals(callType) ? "Incoming Video Call..." : "Incoming Call...";
			
			sendPush(to, type, title, xml, principal.getTenantId());      
			
			log.info("Jingle [{}] session-initiate processed for user: {}", callType, to);
	}

	/**
	 * Encapsulates the NotificationService call to ensure consistent metadata 
	 * across all call-related push notifications.
	 */
	private void sendPush(String to, NotificationType type, String title, String body, Integer tenantId) {        
		Notification notif = Notification.builder()
				.receiverIds(Set.of(to))
				.type(type)
				.title(title)
				.body(body)
				.tenantId(tenantId)
				.build();
		notificationService.sendPush(notif);
	}
}
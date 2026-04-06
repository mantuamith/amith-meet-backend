package com.algomeet.xmpp.chatservice.routing.call;

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
 * <p><strong>Jingle Offline Notification Handler</strong></p>
 * * <p>The {@code JingleNotificationHandler} is responsible for triggering external 
 * push notifications when a Jingle (XEP-0166) session is initiated. This ensures 
 * that recipients who are offline or have backgrounded mobile applications are 
 * alerted to incoming VoIP/Video calls.</p>
 * * <p><b>Core Responsibilities:</b></p>
 * <ul>
 * <li><b>Session-Initiate Detection:</b> Intercepts initial call requests to 
 * trigger out-of-band signaling.</li>
 * <li><b>Push Dispatch:</b> Communicates with the {@code NotificationService} 
 * to send high-priority FCM/APNs payloads (VoIP Push).</li>
 * <li><b>Media Classification:</b> Differentiates between Audio and Video 
 * call types to provide accurate notification content (e.g., CallKit UI).</li>
 * </ul>
 * * <p>Note: This class handles <i>notification</i> only. Real-time signaling 
 * delivery and session state tracking are managed by the routing engine and 
 * {@code CallLifeCycleTracker} respectively.</p>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@AllArgsConstructor
public class JingleNotificationHandler {

	private final NotificationService notificationService;

	/**
	 * Processes incoming stanzas to identify new call sessions requiring 
	 * push notification delivery.
	 * * @param ctx         The Netty {@link ChannelHandlerContext} for the sender.
	 * @param id          The unique IQ stanza ID.
	 * @param to          The JID of the intended recipient.
	 * @param from        The JID of the initiator.
	 * @param xml         The raw Jingle XML payload.
	 * @param principal   The authenticated sender's security context.
	 */
	public void handlePush(ChannelHandlerContext ctx, String id, String to, 
			String from, String xml, XmppPrincipal principal) {

		// 1. Detect media type using quote-agnostic regex per XEP-0167
		boolean isVideo = xml.matches("(?s).*media=['\"]video['\"].*");

		// 2. Filter for 'session-initiate' only; notifications are not required 
		// for subsequent signaling (transport-info, accept, etc.)
		boolean isInitiate = xml.contains("session-initiate");

		if(isInitiate) {
			String callType = isVideo ? "video" : "audio";
			handleCallLogic(ctx, id, to, from, principal, callType);
		}
	}

	/**
	 * Orchestrates the push notification metadata based on the call type.
	 * * @param callType The media type: "video" or "audio".
	 */
	private void handleCallLogic(ChannelHandlerContext ctx, String id, String to, String from, 
			XmppPrincipal principal, String callType) {

		// Map call type to specialized notification types to trigger 
		// device-specific VoIP UI (e.g., Android ConnectionService or iOS CallKit).
		NotificationType type = "video".equals(callType) ? NotificationType.VIDEO_CALL : NotificationType.AUDIO_CALL;
		String title = "video".equals(callType) ? "Incoming Video Call..." : "Incoming Call...";

		sendPush(to, type, title, principal.getTenantId());      

		log.info("Dispatched {} push notification for recipient: {}", callType, to);
	}

	/**
	 * Forwards the notification request to the notification microservice.
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
}
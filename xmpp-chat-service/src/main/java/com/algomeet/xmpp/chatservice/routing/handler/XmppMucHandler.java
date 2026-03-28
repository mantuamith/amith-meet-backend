package com.algomeet.xmpp.chatservice.routing.handler;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.parser.GroupChatParser;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class XmppMucHandler {
	private final XmppArchiveService xmppArchiveService;
	private final GroupClient groupClient;
	private final UserSessionRegistry userSessionRegistry;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final NotificationService notificationService;

	/**
	 * Handles routing for Multi-User Chat rooms.
	 * Strategy: Save every event (Message, Reaction, Retraction) and forward to client.
	 */
	/**
	 * Handles routing for Multi-User Chat rooms.
	 * Strategy: Save every event, rewrite JIDs for anonymity, and forward.
	 */
	public void handleGroupChatRouting(ChannelHandlerContext ctx, String id, String roomJid, String from, String originalXml, String groupChatDomain) {
	    XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
	    
	    String roomId = XmppUtil.getRoomId(roomJid);
	    String updatedXml = originalXml;
	    
	    // 1. Archiving Logic (MAM / ULID Injection)
	    if(XmppStanzaUtil.isArchiveable(originalXml)) {
	        StanzaInfo info = GroupChatParser.parse(originalXml);
	        Ulid ulid = UlidCreator.getMonotonicUlid();
	        String ulidString = ulid.toLowerCase();

	        String stanzaIdExtension = String.format(
	                "<stanza-id xmlns='urn:xmpp:sid:0' by='%s' id='%s'/>", 
	                principal.getDomain(), 
	                ulidString
	                );

	        updatedXml = originalXml.replace("</message>", stanzaIdExtension + "</message>");

	        xmppArchiveService.archiveEvent(updatedXml, info, roomId, from, ulidString)
	            .doOnSuccess(saved -> log.debug("Event archived [{}]: category={}", id, info.getCategory()))
	            .subscribe();
	    }

	    try {
	        MucRoomDto group = groupClient.getGroupById(Long.parseLong(roomId));
	        
	        // Find the sender's member object to get their Nickname
	        Optional<MucMember> senderMucMember = group.getMembers().stream()
	                .filter(m -> m.getUserKey().equals(principal.getUserKey())).findFirst();
	        
	        if(senderMucMember.isEmpty()) {
	            log.error("User {} is not a member of group {}", principal.getUserKey(), roomId);
	            return;
	        }

	        // Define the "Occupant JID" (e.g., room@conference.algomeet.com/nickname)
	        // This is what other users should see in the 'from' attribute.
	        // Remove nickname from sender
	        String tempRoomJid = roomJid;
	        if (tempRoomJid.contains("/")) {
	        	tempRoomJid = tempRoomJid.substring(0, tempRoomJid.lastIndexOf("/"));
	        }
	        // Replace the nickname with actual user's nickname in chat room
	        String occupantFromJid = tempRoomJid + "/" + senderMucMember.get().getNickname();

            // Define the patterns to match both ' and "
            // The regex looks for: from= followed by either ' or " then the JID, then the matching quote
            String fromRegex = "from=['\"]" + Pattern.quote(from) + "['\"]";
            String toRegex = "to=['\"]" + Pattern.quote(roomJid) + "['\"]";
            
	        for(MucMember receiverMucMember : group.getMembers()) {
	            String toUserKey = receiverMucMember.getUserKey();
	            
	            // Don't route back to the sender (unless reflecting presence, but usually handled by client)
	            if (toUserKey.equals(principal.getUserKey())) {
	                //continue;
	            }

	            // --- JID REWRITING LOGIC ---
	            // 1. Replace the real 'from' with the room nickname (Anonymity)
	            // 2. Replace the 'to' (room address) with the recipient's real JID (Delivery)
	            // Perform the replacement
	            String finalForwardXml = updatedXml
	            		.replaceAll(fromRegex, "from='" + occupantFromJid + "'")
	            		.replaceAll(toRegex, "to='" + toUserKey + "@" + groupChatDomain + "'");

	            // Check sessions for live delivery
	            Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
	            boolean hasSessions = !CollectionUtils.isEmpty(userSessions);
	            boolean hasActiveSession = hasSessions && userSessions.stream()
	                    .anyMatch(s -> UserState.ACTIVE == s.getState());

	            if (hasSessions) {
	                if (originalXml.contains("urn:xmpp:jingle:1")) {
	                    // Logic for Jingle MUC routing
	                    // TODO: Logic for Jingle MUC routing
	                } else {
	                    // Standard Message Routing
	                    clusterMessagePublisher.convertAndSendToUser(id, toUserKey, principal.getUserKey(), ChatType.GROUPCHAT, finalForwardXml);
	                }
	            }

	            // Push Notification Logic
	            if (!hasActiveSession) {
	                if (originalXml.contains("urn:xmpp:jingle:1")) {
	                    // Trigger VoIP/Call Notification
	                } else if (XmppStanzaUtil.isPushEligible(originalXml)) {
	                    String body = XmppUtil.getMessageBody(originalXml);
	                    sendPushNotification(toUserKey, body, NotificationType.GROUP_MESSAGE, principal);
	                }
	            }
	        }

	    } catch (NumberFormatException e) {
	        log.error("Invalid roomId format: {}", roomId);
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
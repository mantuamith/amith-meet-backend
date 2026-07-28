package com.algomeet.xmpp.chatservice.util;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.repository.projection.MucMessageView;
import com.algomeet.xmpp.chatservice.stanza.MessageRetractStanza;
import com.algomeet.xmpp.chatservice.stanza.MessageSyncConversationStanza;
import com.algomeet.xmpp.chatservice.stanza.MessageViewSyncStanza;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class MamUtil {
	private final JidUtil jidUtil;
	// Pre-compile patterns outside the method to save CPU
	private static final Pattern TO_ATTR_PATTERN = Pattern.compile("\\s+to='[^']*'");
	private static final Pattern FROM_ATTR_PATTERN = Pattern.compile("from='[^']*'");
		
	/**
	 * Filters messages to ensure Private Messages within a MUC are only visible to the recipient.
	 */
	public static boolean isAuthorized(MucMessage msg, XmppPrincipal principal) {
		return !(msg.getHiddenFromUserKeys() != null && msg.getHiddenFromUserKeys()
				.contains(UUID.fromString(principal.getUserKey())));
	}
	
	public static boolean isAuthorized(MucMessageView msg, XmppPrincipal principal) {
		return !(msg.getHiddenFromUserKeys() != null && msg.getHiddenFromUserKeys()
				.contains(UUID.fromString(principal.getUserKey())));
	}


	public static boolean isPrincipalRecipient(MucMessage msg, XmppPrincipal principal) {
		return (msg.getTo() == null || msg.getTo().compareTo(UUID.fromString(principal.getUserKey())) == 0);
	}
	
	public static boolean isPrincipalRecipient(MucMessageView msg, XmppPrincipal principal) {
		return (msg.getTo() == null || msg.getTo().compareTo(UUID.fromString(principal.getUserKey())) == 0);
	}
	
	public String convertToMamFormat(String fromUserKey, String toRoomId, String msg) {
		/**
		 * Example raw group chat message stanza:
		 *
		 * <message from='2fc35cae-e0b7-40a5-b2aa-e86206730e99@algomeet.app'
		 *          to='289c5f4d-58a0-4def-bf5b-0fd15c045575@conference.algomeet.app'
		 *          type='groupchat'
		 *          id='msg-algomeet-1321199'>
		 *     <body>
		 *         Team, the Netty server is now handling Jingle stanzas correctly!
		 *     </body>
		 *     <stanza-id xmlns='urn:xmpp:sid:0'
		 *                by='algomeet.app'
		 *                id='01kqf1ty089crppav99f5nr50v'/>
		 * </message>
		 */

		int headerEnd = msg.indexOf('>') + 1;
		if (headerEnd <= 0) return msg;

		String header = msg.substring(0, headerEnd);

		if (header.indexOf("\"") != -1) {
			header = header.replaceAll("\"", "'");
		}

		// 1. Remove 'to'
		header = TO_ATTR_PATTERN.matcher(header).replaceAll("");

		// 2. Replace 'from'
		String newFrom = "from='" + jidUtil.getGroupBareJid(toRoomId) + "/" + fromUserKey + "'";
		header = FROM_ATTR_PATTERN.matcher(header).replaceAll(newFrom);

		// 3. Rebuild using StringBuilder to minimize object copies
		return new StringBuilder(header.length() + msg.length() - headerEnd)
				.append(header)
				.append(msg, headerEnd, msg.length())
				.toString();
	}

	
	/**
	 * Builds an XMPP synchronization stanza used to notify client devices
	 * about the current start of the group conversation.
	 *
	 * <p>
	 * The provided stanzaId represents the earliest remaining message in the
	 * conversation. Any messages before this stanzaId should be treated as
	 * permanently deleted and removed from local device storage.
	 * </p>
	 *
	 * @param msg the current first available group message
	 * @param principal the authenticated XMPP principal
	 * @return XML representation of the conversation synchronization stanza
	 */
	public String buildSyncConversationXml(MucMessage msg, XmppPrincipal principal) {

	    // Construct the group bare JID (room@service).
	    String groupJid = jidUtil.getGroupBareJid(msg.getRoomId().toString());
	    MessageSyncConversationStanza syncConversationStanza = MessageSyncConversationStanza.builder()

	            // Unique ID for this synchronization stanza.
	            .id(UuidCreator.getTimeOrderedEpoch().toString())

	            // The stanza originates from the group room.
	            .from(groupJid)

	            // Indicates the current starting point of the conversation.
	            // All messages before this stanzaId are considered hard deleted.
	            .startOfConversationStanzaId((msg.getId() == Constants.NIL_UUID)
	            ? Constants.EMPTY_CONVERSATION_STANZA_ID 
	            		: msg.getId().toString())

	            // Group chat message type.
	            .type(XmppMessageType.GROUPCHAT.getXmlValue())

	            .build();

	    // Serialize the synchronization stanza into XML format.
	    return syncConversationStanza.toXml();
	}
	
	/**
	 * Builds a custom View Management Sync stanza to synchronize "hidden" state across devices.
	 */
	public String buildHideEventXml(MucMessage msg, XmppPrincipal principal) {
		try {
			MessageViewSyncStanza vmSync = MessageViewSyncStanza.builder()
					.id(UuidCreator.getTimeOrderedEpoch().toString())
					.targetId(msg.getMessageId().toString()) // The message ID that should be hidden from view
					.from(principal.getBareJid()) // Sent from the user's bare JID
					.roomId(msg.getRoomId().toString())
					// Removed to attribute to shorten the message
					//.to(principal.getBareJid())   // Sent to self to ensure all connected resources (phone, web) sync
					.build();
			return vmSync.toXml();
		} catch(Exception ex) {
			log.error("Error composing View management sync stanza ", ex);
		}
		return null;
	}
	
	/**
	 * Builds a XEP-0424 Message Retraction stanza for MUC groupchat.
	 */
	public String buildRetractionXml(MucMessage msg, XmppPrincipal principal) {
		String timestamp = XmppStanzaUtil.formatTimestamp(Instant.ofEpochMilli(msg.getDeletedAt()));
		// Construct the Occupant JID (room@service/nick)
		String groupJid = jidUtil.getGroupBareJid(msg.getRoomId().toString()) + "/" + msg.getFrom();

		MessageRetractStanza retractStanza = MessageRetractStanza.builder()
				.id(UuidCreator.getTimeOrderedEpoch().toString()) // Unique ID for this specific retraction stanza
				.from(groupJid)                  // Originating from the room occupant address
				.retractedId(msg.getMessageId().toString()) // The original 'id' of the message to be removed
				.type(XmppMessageType.GROUPCHAT.getXmlValue())
				.stamp(timestamp)
				.build();

		// Injects the server's tracking ID (cursor) into the <stanza-id/> element for MAM/Syncing.
		return XmppStanzaUtil.insertStanzaId(retractStanza.toXml(), msg.getUpdateCursorId().toString(), principal.getDomain());
	}
	
	/**
	 * Constructs a highly optimized XML string block containing multiple reader entries 
	 * from a list of user keys.
	 *
	 * @param userKeys A list of unique participant user keys who have read the message.
	 * @return A joined XML string fragment of empty elements, or an empty string if the list is empty.
	 */
	public static String buildReadersBlock(List<String> userKeys) {
	    if (userKeys == null || userKeys.isEmpty()) {
	        return "";
	    }

	    
	    // Pre-allocate buffer capacity to avoid mid-stream array copies 
	    // (Estimate ~65 characters per reader tag block)
	    StringBuilder xmlBuilder = new StringBuilder(userKeys.size() * 65);
	    
	    for (String userKey : userKeys) {
	        if (userKey != null && !userKey.isBlank()) {
	            xmlBuilder.append("<reader user-key='")
	                      .append(userKey)
	                      .append("'/>");
	        }
	    }
	    
	    return xmlBuilder.toString();
	}
}

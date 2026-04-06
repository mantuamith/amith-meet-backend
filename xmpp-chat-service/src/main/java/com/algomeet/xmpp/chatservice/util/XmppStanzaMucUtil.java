package com.algomeet.xmpp.chatservice.util;

import java.util.regex.Pattern;

import com.algomeet.xmpp.chatservice.dto.MucMember;

public class XmppStanzaMucUtil {

	/**
	 * Rewrites the MUC stanza for delivery to a specific recipient.
	 * Ensures the 'from' JID is the anonymous Occupant JID and 'to' is the recipient's real JID.
	 */
	public static String rewriteMucStanzaForRecipient(String xml, String roomJid, String fromJid, 
	                                            String recipientUserKey, String domain, MucMember sender) {
	    
	    String occupantFromJid = buildOccupantJid(roomJid, sender);
	    String recipientRealJid = recipientUserKey + "@" + domain;

	    // Define patterns to match both single and double quotes for 'from' and 'to'
	    String fromPattern = "from=['\"]" + Pattern.quote(fromJid) + "['\"]";
	    String toPattern = "to=['\"]" + Pattern.quote(roomJid) + "['\"]";

	    return xml.replaceAll(fromPattern, "from='" + occupantFromJid + "'")
	              .replaceAll(toPattern, "to='" + recipientRealJid + "'");
	}

	/**
	 * Constructs the MUC Occupant JID (room@service/nickname).
	 * XEP-0045: The resourcepart of a MUC JID must be the user's room nickname.
	 */
	private static String buildOccupantJid(String roomJid, MucMember sender) {
	    // Strip existing resource if present (e.g., room@service/old-nick -> room@service)
	    String bareRoomJid = roomJid;
	    int slashIndex = roomJid.lastIndexOf('/');
	    if (slashIndex != -1) {
	        bareRoomJid = roomJid.substring(0, slashIndex);
	    }

	    String nickname = sender.getUserKey();

	    return bareRoomJid + "/" + nickname;
	}
}

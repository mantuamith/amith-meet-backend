package com.algomeet.xmpp.chatservice.stanza;

import java.util.Objects;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.enums.ConversationViewAction;

/**
 * Represents a synchronization stanza used to update message visibility 
 * across a user's multiple devices (e.g., hiding a message on mobile and web).
 * * Uses the 'headline' message type to ensure background processing without 
 * affecting unread message counts or triggering notifications.
 */
public class ConversationViewSyncStanza {
	private final String from;
	private final String to;
	private final String id;
	private final String type;
	private final String action;
	private final String roomId;
	private final String peerKey;

	private ConversationViewSyncStanza(Builder builder) {
		this.from = builder.from;
		this.to = builder.to;
		this.id = builder.id;
		this.type = "headline"; // Standard for background sync
		this.action = builder.action;
		this.roomId = builder.roomId;
		this.peerKey = builder.peerKey;	
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Serializes the synchronization command into a valid XMPP XML string.
	 */
	public String toXml() {
		StringBuilder sb = new StringBuilder()
				.append("<message ")
				.append("from='").append(from).append("' ");
		
		// Only append the to attribute if to is provided
		if (to != null && !to.isBlank()) {
			sb.append("to='").append(to).append("' ");
		}
		sb.append("id='").append(id).append("' ")
		.append("type='").append(type).append("'>")
		.append("<query xmlns='" + Constants.NS_CONVERSATION_VIEW +  "'>")
		.append("<item ")
		.append("action='").append(action).append("' ");

		// Only append the roomId attribute if roomId is provided
		if (roomId != null && !roomId.isBlank()) {
			sb.append("room-id='").append(roomId).append("' ");
		}
		
		// Only append the peerKey attribute if peerKey is provided
		if (peerKey != null && !peerKey.isBlank()) {
			sb.append("peer-key='").append(peerKey).append("' ");
		}

		sb.append("'/>")
		.append("</query>")
		.append("</message>");

		return sb.toString();
	}

	// --- Builder Class ---
	public static class Builder {
		private String from;
		private String to;
		private String id;
		private String action = ConversationViewAction.PIN.getValue(); // Default action
		private String roomId;
		private String peerKey;

		public Builder from(String from) {
			this.from = from;
			return this;
		}

		public Builder to(String to) {
			this.to = to;
			return this;
		}

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder action(String action) {
			this.action = action;
			return this;
		}

		public Builder roomId(String room) {
			this.roomId = room;
			return this;
		}
		
		public Builder peerKey(String peer) {
			this.peerKey = peer;
			return this;
		}

		public ConversationViewSyncStanza build() {
			Objects.requireNonNull(from, "From JID is required");
			Objects.requireNonNull(id, "Stanza ID is required");
			return new ConversationViewSyncStanza(this);
		}
	}
}
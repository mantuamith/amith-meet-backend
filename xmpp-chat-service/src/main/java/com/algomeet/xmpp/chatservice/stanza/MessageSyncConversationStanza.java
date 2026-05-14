package com.algomeet.xmpp.chatservice.stanza;

import java.util.Objects;

/**
 * Represents an XMPP Message Stanza for Conversation Synchronization.
 * 
 * <message
 *     from='room1@conference.example.com'
 *     to='bob@example.com/mobile'
 *     type='groupchat'
 *     id='sync-delete-001'>
 *
 *     <sync-conversation xmlns='urn:custom:conversation-sync:0'>
 *         <start-of-conversation stanza-id='msg-150'/>
 *     </sync-conversation>
 * </message>
 */
public class MessageSyncConversationStanza {
	private final String from;
	private final String to;
	private final String type;
	private final String id;
	private final String startOfConversationStanzaId;

	private MessageSyncConversationStanza(Builder builder) {
		this.from = builder.from;
		this.to = builder.to;
		this.type = builder.type;
		this.id = builder.id;
		this.startOfConversationStanzaId = builder.startOfConversationStanzaId;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Converts the object to a valid XML String according to the custom sync schema.
	 */
	public String toXml() {
		StringBuilder sb = new StringBuilder()
				.append("<message ")
				.append("from='").append(from).append("' ");
		
		// Only append the to attribute if to is provided
		if (to != null && !to.isBlank()) {
			sb.append("to='").append(to).append("' ");
		}
		
		sb.append("type='").append(type).append("' ")
		.append("id='").append(id).append("'>")
		.append("<sync-conversation xmlns='urn:custom:conversation-sync:0'>")
		.append("<start-of-conversation stanza-id='").append(startOfConversationStanzaId).append("'/>")
		.append("</sync-conversation>")
		.append("</message>");

		return sb.toString();
	}

	// --- Getters ---
	public String getFrom() { return from; }
	public String getTo() { return to; }
	public String getType() { return type; }
	public String getId() { return id; }
	public String getStartOfConversationStanzaId() { return startOfConversationStanzaId; }

	// --- Builder Class ---
	public static class Builder {
		private String from;
		private String to;
		private String type = "groupchat";
		private String id;
		private String startOfConversationStanzaId;

		public Builder from(String from) {
			this.from = from;
			return this;
		}

		public Builder to(String to) {
			this.to = to;
			return this;
		}

		public Builder type(String type) {
			this.type = type;
			return this;
		}

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder startOfConversationStanzaId(String stanzaId) {
			this.startOfConversationStanzaId = stanzaId;
			return this;
		}

		public MessageSyncConversationStanza build() {
			Objects.requireNonNull(from, "From JID cannot be null");
			Objects.requireNonNull(id, "Message ID cannot be null");
			Objects.requireNonNull(startOfConversationStanzaId, "start-of-conversation stanza-id cannot be null");

			return new MessageSyncConversationStanza(this);
		}
	}
}
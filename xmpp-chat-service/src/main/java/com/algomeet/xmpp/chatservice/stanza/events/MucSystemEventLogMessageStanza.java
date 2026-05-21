package com.algomeet.xmpp.chatservice.stanza.events;

import java.util.Objects;

import com.algomeet.xmpp.chatservice.enums.MucEventType;

/**
 * Represents an XMPP Message Stanza for System Events (e.g., member_added).
 * 
 * <message id='...' from='...' to='...' type='groupchat'>
 *   <body>...</body>
 *   <x xmlns='http://algomeet.app/protocol/system'>
 *     <event type='member_added' jid='...'/>
 *   </x>
 * </message>
 */
public class MucSystemEventLogMessageStanza {
	private final String id;
	private final String from;
	private final String to;
	private final String type;
	private final String body;
	private final MucEventType eventType;
	private final String eventJid;

	private MucSystemEventLogMessageStanza(Builder builder) {
		this.id = builder.id;
		this.from = builder.from;
		this.to = builder.to;
		this.type = builder.type;
		this.body = builder.body;
		this.eventType = builder.eventType;
		this.eventJid = builder.eventJid;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Converts the object to a valid XML String according to the system event protocol.
	 */
	public String toXml() {
		StringBuilder sb = new StringBuilder()
				.append("<message ")
				.append("id='").append(id).append("' ")
				.append("from='").append(from).append("' ");

		// Only append the to attribute if to is provided
		if (to != null && !to.isBlank()) {
			sb.append("to='").append(to).append("' ");
		}

		sb.append("type='").append(type).append("'>")
				.append("<body>").append(body).append("</body>")
				.append("<x xmlns='http://algomeet.app/protocol/system'>")
				.append("<event type='").append(eventType.getXmlValue()).append("' jid='").append(eventJid).append("'/>")
				.append("</x>")
				.append("</message>");

		return sb.toString();
	}

	// --- Getters ---
	public String getId() { return id; }
	public String getFrom() { return from; }
	public String getTo() { return to; }
	public String getType() { return type; }
	public String getBody() { return body; }
	public MucEventType getEventType() { return eventType; }
	public String getEventJid() { return eventJid; }

	// --- Builder Class ---
	public static class Builder {
		private String id;
		private String from;
		private String to;
		private String type = "groupchat";
		private String body;
		private MucEventType eventType;
		private String eventJid;

		public Builder id(String id) {
			this.id = id;
			return this;
		}

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

		public Builder body(String body) {
			this.body = body;
			return this;
		}

		public Builder eventType(MucEventType eventType) {
			this.eventType = eventType;
			return this;
		}

		public Builder eventJid(String eventJid) {
			this.eventJid = eventJid;
			return this;
		}

		public MucSystemEventLogMessageStanza build() {
			Objects.requireNonNull(id, "Message ID cannot be null");
			Objects.requireNonNull(from, "From JID cannot be null");
			Objects.requireNonNull(eventType, "Event type cannot be null");
			Objects.requireNonNull(eventJid, "Event targeted JID cannot be null");

			return new MucSystemEventLogMessageStanza(this);
		}
	}
}
package com.algomeet.xmpp.chatservice.stanza;

import java.util.Objects;

/**
 * Represents an XMPP Message Retraction stanza (XEP-0424).
 */
public class MessageRetractStanza {
	private final String from;
	private final String to;
	private final String type;
	private final String id;
	private final String retractedId;
	private final String stamp;
	private final String reasonText;

	private MessageRetractStanza(Builder builder) {
		this.from = builder.from;
		this.to = builder.to;
		this.type = builder.type;
		this.id = builder.id;
		this.retractedId = builder.retractedId;
		this.stamp = builder.stamp;
		this.reasonText = builder.reasonText;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Converts the object to a valid XML String.
	 */
	public String toXml() {
		StringBuilder sb = new StringBuilder()
				.append("<message from='").append(from).append("' ");

		// Only append the to attribute if to is provided
		if (to != null && !to.isBlank()) {
			sb.append("to='").append(to).append("' ");
		}

		sb.append("type='").append(type).append("' ")
		.append("id='").append(id).append("'>")
		.append("<retracted xmlns='urn:xmpp:message-retract:1' ")
		.append("id='").append(retractedId).append("' ")
		.append("stamp='").append(stamp).append("'>");

		if (reasonText != null) {
			sb.append(reasonText);
		}

		sb.append("</retracted>")
		.append("</message>");

		return sb.toString();
	}

	// --- Builder Class ---
	public static class Builder {
		private String from;
		private String to;
		private String type = "groupchat"; // Default for MUC
		private String id;
		private String retractedId;
		private String stamp;
		private String reasonText; // Default text

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

		public Builder retractedId(String retractedId) {
			this.retractedId = retractedId;
			return this;
		}

		public Builder stamp(String stamp) {
			this.stamp = stamp;
			return this;
		}

		public Builder reasonText(String reasonText) {
			this.reasonText = reasonText;
			return this;
		}

		public MessageRetractStanza build() {
			Objects.requireNonNull(from, "From JID cannot be null");
			Objects.requireNonNull(id, "Message ID cannot be null");
			Objects.requireNonNull(retractedId, "Retracted ID (target) cannot be null");
			Objects.requireNonNull(stamp, "Timestamp cannot be null");

			return new MessageRetractStanza(this);
		}
	}
}